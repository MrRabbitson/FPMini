package lol.mrrabbitson.fpmini.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Разбор страниц продавца: чаты, сообщения, продажи, мои лоты, заявки в поддержку. */
object SellerParser {

    // ---------- чаты ----------

    /** Список диалогов: страница /chat/ или html из runner (chat_bookmarks). */
    fun chatList(html: String): List<ChatPreview> =
        Jsoup.parseBodyFragment(html).select("a.contact-item").mapNotNull { a ->
            val node = a.attr("data-id").ifBlank {
                Regex("node=([\\w-]+)").find(a.attr("href"))?.groupValues?.get(1).orEmpty()
            }
            if (node.isBlank()) return@mapNotNull null
            ChatPreview(
                node = node,
                name = a.selectFirst(".media-user-name")?.text()?.trim().orEmpty(),
                avatarUrl = Parser.imageUrl(a.selectFirst(".avatar-photo")),
                lastMessage = a.selectFirst(".contact-item-message")?.text()?.trim().orEmpty(),
                time = a.selectFirst(".contact-item-time")?.text()?.trim().orEmpty(),
                unread = a.hasClass("unread"),
                lastMsgId = a.attr("data-node-msg").toLongOrNull(),
            )
        }

    /** Сообщения одного чата: страница /chat/?node=… или html из runner. */
    fun messages(scope: Element): List<ChatMessage> {
        val out = mutableListOf<ChatMessage>()
        var authorId: Int? = null
        var authorName = ""
        var time = ""
        for (item in scope.select(".chat-msg-item")) {
            val id = item.attr("id").removePrefix("message-").toLongOrNull() ?: continue
            val link = item.selectFirst(".chat-msg-author-link")
            if (link != null) {
                authorName = link.text().trim()
                authorId = Parser.userIdFromHref(link.attr("href"))
            } else {
                val nameEl = item.selectFirst(".media-user-name")
                if (nameEl != null && nameEl.ownText().isNotBlank()) {
                    authorName = nameEl.ownText().trim()
                    authorId = 0 // FunPay / системное
                }
            }
            item.selectFirst(".chat-msg-date")?.let { d -> time = d.attr("title").ifBlank { d.text() }.trim() }
            val img = item.selectFirst("a.chat-img-link")?.attr("href")?.let { Parser.absolute(it) }
            out += ChatMessage(
                id = id,
                authorId = authorId,
                authorName = authorName,
                text = Parser.textWithBreaks(item.selectFirst(".chat-msg-text")),
                time = time,
                imageUrl = img,
            )
        }
        return out
    }

    fun chat(html: String, node: String): ChatPage {
        val d = Parser.doc(html)
        val nameLink = d.selectFirst(".chat-header .media-user-name a")
        val media = nameLink?.parents()?.firstOrNull { it.hasClass("media") }
        val who = nameLink?.let {
            UserShort(
                id = Parser.userIdFromHref(it.attr("href")),
                name = it.text().trim(),
                avatarUrl = Parser.imageUrl(media?.selectFirst(".avatar-photo")),
                status = media?.selectFirst(".media-user-status")?.text()?.trim(),
            )
        }
        val list = d.selectFirst(".chat-message-list") ?: d.body()
        return ChatPage(node, who, messages(list))
    }

    // ---------- продажи ----------

    fun orders(html: String): List<Order> = Parser.doc(html).select("a.tc-item").mapNotNull { row ->
        val id = Regex("/orders/([A-Za-z0-9]+)").find(row.attr("href"))?.groupValues?.get(1) ?: return@mapNotNull null
        val statusEl = row.selectFirst(".tc-status")
        val statusText = statusEl?.text()?.trim().orEmpty()
        val status = when {
            row.hasClass("warning") || statusText.contains("озврат") -> OrderStatus.REFUND
            statusEl?.hasClass("text-primary") == true || statusText.contains("плач", true) -> OrderStatus.PAID
            else -> OrderStatus.CLOSED
        }
        val priceEl = row.selectFirst(".tc-price")
        val buyerName = row.selectFirst(".media-user-name")?.text()?.trim()
        Order(
            id = id,
            description = row.selectFirst(".order-desc > div")?.text()?.trim()
                ?: row.selectFirst(".order-desc")?.text()?.trim().orEmpty(),
            category = row.selectFirst(".order-desc .text-muted")?.text()?.trim().orEmpty(),
            buyer = buyerName?.let {
                UserShort(
                    id = Parser.userIdFromHref(row.selectFirst("[data-href*=/users/]")?.attr("data-href")),
                    name = it,
                    avatarUrl = Parser.imageUrl(row.selectFirst(".avatar-photo")),
                    status = null,
                )
            },
            priceText = priceEl?.text()?.trim().orEmpty(),
            priceValue = Parser.parseNumber(priceEl?.text()),
            date = row.selectFirst(".tc-date-time")?.text()?.trim().orEmpty(),
            status = status,
            statusText = statusText.ifBlank {
                when (status) { OrderStatus.PAID -> "Оплачен"; OrderStatus.CLOSED -> "Закрыт"; OrderStatus.REFUND -> "Возврат" }
            },
        )
    }

    // ---------- мои лоты ----------

    fun myLots(profileHtml: String, nodeToGame: Map<Int, Int>): List<MyLotGroup> =
        Parser.doc(profileHtml).select(".offer").mapNotNull { o ->
            val container = o.selectFirst(".offer-list-title-container")
            val a = o.selectFirst(".offer-list-title h3 a, .offer-list-title a") ?: return@mapNotNull null
            val ref = Parser.categoryRef(a.attr("href"), a.text().trim()) ?: return@mapNotNull null
            MyLotGroup(
                nodeId = ref.id,
                type = ref.type,
                title = ref.name,
                gameId = container?.attr("data-game-id")?.toIntOrNull() ?: nodeToGame[ref.id],
                offers = o.select("a.tc-item").mapNotNull { Parser.offerRow(it) },
            )
        }

    /** id раздела → id игры, из главной страницы. */
    fun nodeToGame(home: HomePage): Map<Int, Int> =
        home.games.flatMap { g -> g.categories.map { it.id to g.id } }.toMap()

    // ---------- поддержка (support.funpay.com) ----------

    fun tickets(html: String): List<Ticket> = Parser.doc(html, "https://support.funpay.com").select("a.ticket-item").mapNotNull { a ->
        val id = a.attr("href").trimEnd('/').substringAfterLast('/').ifBlank { return@mapNotNull null }
        val badge = a.selectFirst(".badge")
        val kind = when {
            badge == null -> "other"
            badge.hasClass("bg-danger") -> "open"
            badge.hasClass("bg-warning") -> "waiting"
            badge.hasClass("bg-success") -> "solved"
            badge.hasClass("bg-secondary") -> "closed"
            else -> "other"
        }
        Ticket(
            id = id,
            title = (a.selectFirst(".col-12.mt-2") ?: a.selectFirst(".col-12"))?.text()?.trim().orEmpty().ifBlank { "Заявка #$id" },
            status = badge?.text()?.trim().orEmpty(),
            statusKind = kind,
            date = a.selectFirst(".text-secondary")?.text()?.trim().orEmpty(),
        )
    }

    fun hasNextTicketsPage(html: String): Boolean =
        Parser.doc(html).selectFirst("a.page-link[rel=next]") != null

    fun ticket(html: String, id: String): TicketPage {
        val d = Parser.doc(html, "https://support.funpay.com")
        val title = d.selectFirst(".breadcrumb-item.active")?.text()?.replace("Заявка #$id", "")?.trim()
            ?.ifBlank { null } ?: "Заявка #$id"
        val comments = d.select(".ticket-comment").map { c ->
            TicketComment(
                author = c.selectFirst(".username")?.text()?.trim() ?: "—",
                text = Parser.textWithBreaks(c.selectFirst(".comment-text")),
                time = (c.selectFirst(".comment-username span:nth-child(2)") ?: c.selectFirst(".d-sm-none span:nth-child(2)"))
                    ?.text()?.trim().orEmpty(),
            )
        }
        return TicketPage(
            id = id,
            title = title,
            status = d.selectFirst(".ticket-info-panel .badge")?.text()?.trim(),
            comments = comments,
            replyToken = d.selectFirst("input[name='add_comment[_token]']")?.attr("value"),
        )
    }
}

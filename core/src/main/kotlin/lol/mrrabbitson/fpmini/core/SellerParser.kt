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
            val textEl = item.selectFirst(".chat-msg-text")
            out += ChatMessage(
                id = id,
                authorId = authorId,
                authorName = authorName,
                text = Parser.textWithBreaks(textEl, preserveNewlines = true),
                time = time,
                imageUrl = img,
                links = textEl?.select("a[href]")?.mapNotNull { a ->
                    val t = a.text().trim()
                    if (t.isEmpty()) null else MsgLink(t, Parser.absolute(a.attr("href")))
                }.orEmpty(),
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
            }.replaceFirstChar { it.uppercase() },
            timeMs = FpDates.parse(row.selectFirst(".tc-date-time")?.text().orEmpty()),
        )
    }

    /** Страница продаж + токен для следующей страницы (форма «Показать ещё»). */
    fun ordersPage(html: String): OrdersPage = OrdersPage(
        orders(html),
        Parser.doc(html).selectFirst("input[name=continue]")?.attr("value")?.ifBlank { null },
    )

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

    // ---------- страница заказа ----------

    fun order(html: String, id: String): OrderPage {
        val d = Parser.doc(html)
        var amount: String? = null
        var goods: String? = null
        val params = mutableListOf<Param>()
        for (p in d.select(".param-item")) {
            val label = p.selectFirst("h5")?.text()?.trim() ?: continue
            // «Действия» — это кнопки (возврат и т.п.), не данные заказа
            if (label.contains("Действия", true) || label.contains("Actions", true)) continue
            if (label.contains("Сумма", true) || label.contains("Total", true)) {
                val big = p.selectFirst("span.h1")?.text()?.trim()
                val cur = p.select("strong").lastOrNull()?.text()?.trim()
                amount = listOfNotNull(big, cur).joinToString(" ").ifBlank { null }
                    ?: p.clone().also { it.select("h5").remove() }.text().trim().ifBlank { null }
                continue
            }
            if (label.contains("Оплаченн", true) || label.contains("Paid product", true)) {
                goods = if (p.selectFirst("span.secret-placeholder") != null) "Скрыт (выдан автоматически)"
                else Parser.textWithBreaks(p.clone().also { it.select("h5").remove() }).ifBlank { null }
                continue
            }
            val clone = p.clone(); clone.select("h5").remove(); clone.select("button, form, .btn").remove()
            val v = Parser.textWithBreaks(clone)
            if (v.isNotBlank() && params.none { it.label == label }) params += Param(label, v)
        }
        val header = d.selectFirst("h1.page-header, .page-header")
        val status = header?.selectFirst(".text-success, .text-warning, .text-primary, .text-danger")?.text()?.trim()?.ifBlank { null }
            ?: header?.select("span")?.lastOrNull()?.text()?.trim()?.ifBlank { null }
        val nameLink = d.selectFirst(".chat-header .media-user-name a, .chat-float .media-user-name a")
        val media = nameLink?.parents()?.firstOrNull { it.hasClass("media") }
        val buyer = nameLink?.let {
            UserShort(Parser.userIdFromHref(it.attr("href")), it.text().trim(), Parser.imageUrl(media?.selectFirst(".avatar-photo")),
                media?.selectFirst(".media-user-status")?.text()?.trim())
        }
        val chatNode = d.selectFirst(".chat[data-id]")?.attr("data-id")?.ifBlank { null }
            ?: d.selectFirst("[data-name^='users-']")?.attr("data-name")
        return OrderPage(id, status?.replaceFirstChar { it.uppercase() }, amount, goods,
            params.filterNot { it.label.contains("чат", true) }, buyer, chatNode,
            canRefund = d.selectFirst("button.btn-refund") != null)
    }

    // ---------- новая заявка ----------

    fun ticketCategories(html: String): List<TicketCategory> =
        Parser.doc(html, "https://support.funpay.com").select("select#ticket_select_form option, select[name*=form] option").mapNotNull { o ->
            val v = o.attr("value").trim(); val t = o.text().trim()
            if (v.isEmpty() || t.startsWith("Выберите")) null else TicketCategory(v, t)
        }.distinctBy { it.id }

    fun ticketForm(html: String, categoryId: String): TicketForm {
        val d = Parser.doc(html, "https://support.funpay.com")
        val fields = mutableListOf<TicketField>()
        for (el in d.select("input[name^='ticket[fields]'], select[name^='ticket[fields]'], textarea[name^='ticket[comment]']")) {
            val name = el.attr("name")
            if (name.contains("_token") || name.contains("attachments")) continue
            if (el.attr("type") == "hidden") continue
            if (fields.any { it.name == name }) continue
            val box = el.closest(".mb-3") ?: el.closest("fieldset") ?: el.parent()
            val labelEl = box?.selectFirst("label[for='${el.id()}']")?.takeIf { el.id().isNotBlank() }
                ?: box?.selectFirst("legend") ?: box?.selectFirst("label")
            val rawLabel = labelEl?.text()?.trim().orEmpty()
            val required = el.hasAttr("required") || labelEl?.hasClass("required") == true || rawLabel.contains("*")
            val label = rawLabel.replace("*", "").trim()
            when {
                el.tagName() == "select" -> fields += TicketField(name, label, "select", required,
                    el.select("option").mapNotNull { o ->
                        val v = o.attr("value"); val t = o.text().trim()
                        if (v.isEmpty() || t.startsWith("Выберите")) null else FieldOption(v, t)
                    })
                el.attr("type") == "radio" -> {
                    val group = (el.closest("fieldset") ?: box)?.select("input[type=radio][name='$name']") ?: listOf(el)
                    val opts = group.mapNotNull { r ->
                        val l = (r.closest("fieldset") ?: box)?.selectFirst("label[for='${r.id()}']")?.text()?.trim()
                        if (r.attr("value").isEmpty()) null else FieldOption(r.attr("value"), l ?: r.attr("value"))
                    }
                    val legend = (el.closest("fieldset")?.selectFirst("legend")?.text() ?: label).replace("*", "").trim()
                    fields += TicketField(name, legend, "radio", required, opts)
                }
                el.tagName() == "textarea" -> fields += TicketField(name, if (name.contains("comment")) "Сообщение" else label, "textarea", required, emptyList())
                else -> fields += TicketField(name, label, "text", required, emptyList())
            }
        }
        // поле сообщения есть всегда, даже если на странице оно сделано редактором
        if (fields.none { it.kind == "textarea" }) fields += TicketField("ticket[comment][body_html]", "Сообщение", "textarea", true, emptyList())
        return TicketForm(categoryId, fields, d.selectFirst("input[name='ticket[_token]']")?.attr("value"))
    }
}

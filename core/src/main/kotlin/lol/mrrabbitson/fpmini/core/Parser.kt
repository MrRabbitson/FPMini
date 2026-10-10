package lol.mrrabbitson.fpmini.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * Разбор HTML-страниц FunPay. Без сети и без Android — только Jsoup,
 * чтобы разбор можно было тестировать отдельно.
 */
object Parser {

    const val BASE = "https://funpay.com"

    fun doc(html: String, url: String = BASE): Document = Jsoup.parse(html, url)

    // ---------- общие ----------

    fun appData(doc: Document): AppData {
        val raw = doc.body().attr("data-app-data")
        val uid = Regex("\"userId\"\\s*:\\s*\"?(\\d+)").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        val csrf = Regex("\"csrf-token\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
            ?: doc.selectFirst("input[name=csrf_token]")?.attr("value")
        return AppData(uid?.takeIf { it > 0 }, csrf)
    }

    fun isLoggedIn(doc: Document): Boolean = appData(doc).userId != null

    /** Ссылки вида /lots/123/ и /chips/45/ → CategoryRef */
    fun categoryRef(href: String, name: String): CategoryRef? {
        val m = Regex("/(lots|chips)/(\\d+)").find(href) ?: return null
        return CategoryRef(m.groupValues[2].toInt(), m.groupValues[1], name)
    }

    fun userIdFromHref(href: String?): Int? =
        href?.let { Regex("/users/(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }

    fun offerIdFromHref(href: String): String? =
        Regex("[?&]id=([\\w-]+)").find(href)?.groupValues?.get(1)

    /** URL картинки из style="background-image: url(...)" или из <img>. */
    fun imageUrl(el: Element?): String? {
        if (el == null) return null
        val style = el.attr("style")
        Regex("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)").find(style)?.let { return absolute(it.groupValues[1]) }
        val img = if (el.tagName() == "img") el else el.selectFirst("img")
        val src = img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src")?.ifBlank { null }
        return src?.let { absolute(it) }
    }

    fun absolute(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> BASE + url
        else -> url
    }

    /**
     * Текст с сохранением переносов строк (<br>, <p>, <div>).
     * [preserveNewlines] — сохранять и обычные переводы строк в тексте (сообщения чата хранят их как есть).
     */
    fun textWithBreaks(el: Element?, preserveNewlines: Boolean = false): String {
        if (el == null) return ""
        val sb = StringBuilder()
        fun walk(e: Element) {
            for (n in e.childNodes()) {
                when (n) {
                    is TextNode -> sb.append(if (preserveNewlines) n.wholeText.replace("\r", "") else n.text())
                    is Element -> {
                        if (n.tagName() == "br") { sb.append('\n'); continue }
                        val block = n.isBlock
                        if (block && sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                        walk(n)
                        if (block && !sb.endsWith("\n")) sb.append('\n')
                    }
                }
            }
        }
        walk(el)
        return sb.toString()
            .lines().joinToString("\n") { it.replace(Regex("[ \t\u00a0]+"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** "1 234.56 ₽" → 1234.56 */
    fun parseNumber(text: String?): Double? {
        if (text == null) return null
        val m = Regex("(\\d[\\d\\s ]*(?:[.,]\\d+)?)").find(text) ?: return null
        return m.groupValues[1].replace(Regex("[\\s ]"), "").replace(',', '.').toDoubleOrNull()
    }

    /** Обложка раздела/профиля: первая фоновая картинка, которая не аватар. */
    fun cover(doc: Document): String? {
        val candidates = doc.select(
            ".cd-wide-bg, .profile-header-bg, .profile-cover, .cover, [class*=cover], [class*=-bg][style*=background]"
        ) + doc.select("[style*=background-image]")
        return candidates.firstOrNull { el ->
            val cls = el.className()
            !cls.contains("avatar") && !cls.contains("photo") && imageUrl(el) != null
        }?.let { imageUrl(it) }
    }

    // ---------- главная ----------

    fun home(html: String): HomePage {
        val d = doc(html)
        val games = d.select(".promo-game-item").mapNotNull { item ->
            val titleEl = item.selectFirst(".game-title") ?: return@mapNotNull null
            val name = titleEl.text().trim().ifBlank { return@mapNotNull null }
            val id = titleEl.attr("data-id").toIntOrNull()
            // У некоторых игр несколько регионов — по одному <ul> на каждый; берём совпадающий с заголовком.
            val lists = item.select("ul.list-inline")
            val list = lists.firstOrNull { it.attr("data-id").isNotBlank() && it.attr("data-id") == titleEl.attr("data-id") }
                ?: lists.firstOrNull()
            val cats = list?.select("li a")?.mapNotNull { a ->
                categoryRef(a.attr("href"), a.text().trim())
            }.orEmpty()
            val gameId = id ?: cats.firstOrNull()?.id ?: return@mapNotNull null
            Game(gameId, name, cats)
        }
        val ad = appData(d)
        val myName = d.selectFirst(".user-link-dropdown .user-link-name, .user-link-name")?.text()?.trim()
        return HomePage(games.distinctBy { it.name.lowercase() }, ad.userId, myName)
    }

    // ---------- раздел (список лотов) ----------

    fun offerRow(a: Element): OfferRow? {
        val href = a.attr("href")
        val id = offerIdFromHref(href) ?: return null
        val type = if (href.contains("/chips/")) "chips" else "lots"
        val desc = a.selectFirst(".tc-desc-text")?.text()?.trim()
            ?: a.selectFirst(".tc-desc")?.text()?.trim()
            ?: ""
        val priceEl = a.selectFirst(".tc-price")
        val priceText = priceEl?.text()?.trim().orEmpty()
        val priceValue = priceEl?.attr("data-s")?.toDoubleOrNull() ?: parseNumber(priceText)
        val sellerName = a.selectFirst(".tc-user .media-user-name, .media-user-name")?.text()?.trim()
        val seller = sellerName?.let {
            UserShort(
                id = userIdFromHref(a.selectFirst(".tc-user [data-href], .avatar-photo[data-href]")?.attr("data-href")),
                name = it,
                avatarUrl = imageUrl(a.selectFirst(".avatar-photo")),
                status = a.selectFirst(".media-user-status")?.text()?.trim(),
            )
        }
        return OfferRow(
            id = id,
            type = type,
            description = desc,
            server = a.selectFirst(".tc-server")?.text()?.trim()?.ifBlank { null },
            amount = a.selectFirst(".tc-amount")?.text()?.trim()?.ifBlank { null },
            priceText = priceText,
            priceValue = priceValue,
            seller = seller,
        )
    }

    fun category(html: String, id: Int, type: String): CategoryPage {
        val d = doc(html)
        val title = d.selectFirst("h1")?.text()?.trim()
            ?: d.title().substringBefore(" — ").trim()
        val siblings = d.select("a.counter-item").mapNotNull { a ->
            val ref = categoryRef(a.attr("href"), "") ?: return@mapNotNull null
            CategorySibling(
                id = ref.id,
                type = ref.type,
                name = a.selectFirst(".counter-param")?.text()?.trim() ?: a.ownText().trim(),
                count = a.selectFirst(".counter-value")?.text()?.trim(),
                active = a.hasClass("active"),
            )
        }
        val offers = d.select("a.tc-item").mapNotNull { offerRow(it) }
        return CategoryPage(id, type, title, cover(d), siblings, offers)
    }

    // ---------- рейтинг и отзывы ----------

    fun rating(scope: Element): Rating? {
        val value = scope.selectFirst(".rating-value .big")?.text()?.trim()
        val count = scope.selectFirst(".rating-full-count")?.text()?.trim()
        if (value == null && count == null) return null
        val bars = scope.select(".rating-full-item .progress-bar, .rating-progress [style*=width]")
            .mapNotNull { Regex("width:\\s*([\\d.]+)%").find(it.attr("style"))?.groupValues?.get(1)?.toFloatOrNull() }
            .map { (it / 100f).coerceIn(0f, 1f) }
        return Rating(value, count, if (bars.size == 5) bars else emptyList())
    }

    fun reviews(scope: Element): List<Review> = scope.select(".review-container").mapNotNull { r ->
        val text = textWithBreaks(r.selectFirst(".review-item-text"))
        val stars = r.select(".review-item-rating [class*=rating]")
            .firstNotNullOfOrNull { Regex("rating(\\d)").find(it.className())?.groupValues?.get(1)?.toIntOrNull() }
        Review(
            authorAvatar = imageUrl(r.selectFirst(".review-item-photo img, .review-item-photo, .avatar-photo")),
            date = r.selectFirst(".review-item-date")?.text()?.trim(),
            detail = r.selectFirst(".review-item-detail")?.text()?.trim(),
            stars = stars,
            text = text,
            answer = r.selectFirst(".review-item-answer div, .review-item-answer")?.let { textWithBreaks(it) }?.ifBlank { null },
        )
    }

    // ---------- страница лота ----------

    fun paymentMethod(opt: Element): PaymentMethod? {
        val value = opt.attr("value")
        if (value.isBlank()) return null
        val content = opt.attr("data-content")
        val (title, price) = if (content.isNotBlank()) {
            val c = Jsoup.parseBodyFragment(content)
            val t = c.selectFirst(".payment-title")?.text()?.trim() ?: c.text().trim()
            val p = c.selectFirst(".payment-value")?.text()?.trim()
            t to p
        } else opt.text().trim() to null
        return PaymentMethod(value, title, price, parseNumber(price))
    }

    fun offer(html: String, id: String): OfferPage {
        val d = doc(html)
        val back = d.selectFirst("a.js-back-link")
        val catTitle = back?.text()?.trim()?.ifBlank { null }
        val catRef = back?.let { categoryRef(it.attr("href"), catTitle.orEmpty()) }

        val params = d.select(".param-list .param-item, .param-item").distinct().mapNotNull { p ->
            val label = p.selectFirst("h5")?.text()?.trim() ?: return@mapNotNull null
            val clone = p.clone()
            clone.select("h5").remove()
            val value = textWithBreaks(clone)
            if (value.isBlank()) null else Param(label, value)
        }.distinctBy { it.label }

        val nameLink = d.selectFirst(".chat-header .media-user-name a, .chat-float .media-user-name a, .media-user-name a")
        val media = nameLink?.parents()?.firstOrNull { it.hasClass("media") }
        val seller = nameLink?.let {
            UserShort(
                id = userIdFromHref(it.attr("href")),
                name = it.text().trim(),
                avatarUrl = imageUrl(media?.selectFirst(".avatar-photo")),
                status = (media ?: d).selectFirst(".media-user-status")?.text()?.trim(),
            )
        }

        val methods = d.select("select[name=method] option").mapNotNull { paymentMethod(it) }
        val unit = params.firstOrNull { it.label.contains("налич", true) }
            ?.value?.let { Regex("(шт\\.?|[A-Za-zА-Яа-я]+\\.?)\\s*$").find(it)?.groupValues?.get(1) }

        return OfferPage(
            id = id,
            categoryTitle = catTitle,
            categoryRef = catRef,
            coverUrl = cover(d),
            params = params.filterNot { it.label.contains("рейтинг", true) },
            seller = seller,
            methods = methods,
            unitLabel = unit,
            rating = rating(d),
            reviews = reviews(d),
        )
    }

    // ---------- профиль ----------

    fun profile(html: String, id: Int): ProfilePage {
        val d = doc(html)
        val name = d.selectFirst("h1 span.mr4")?.text()?.trim()
            ?: d.selectFirst(".profile-header .media-user-name, h1")?.ownText()?.trim()
            ?: d.title().substringBefore(" — ").trim()
        val header = d.selectFirst(".profile-header") ?: d.body()
        val user = UserShort(
            id = id,
            name = name,
            avatarUrl = imageUrl(header.selectFirst(".profile-photo, .avatar-photo")),
            status = header.selectFirst(".media-user-status, .profile-header-status")?.text()?.trim(),
        )
        val registered = d.select(".param-item").firstOrNull {
            it.selectFirst("h5")?.text()?.contains("регистрац", true) == true
        }?.let { p -> p.clone().also { it.select("h5").remove() }.let { textWithBreaks(it) } }

        val sections = d.select(".offer").mapNotNull { o ->
            val a = o.selectFirst(".offer-list-title h3 a, .offer-list-title a") ?: return@mapNotNull null
            val rows = o.select("a.tc-item").mapNotNull { offerRow(it) }
            if (rows.isEmpty()) null
            else ProfileSection(a.text().trim(), categoryRef(a.attr("href"), a.text().trim()), rows)
        }

        val total = d.selectFirst(".rating-full-count a, .rating-full-count")?.text()?.trim()
        return ProfilePage(
            id = id,
            user = user,
            coverUrl = cover(d),
            registered = registered,
            rating = rating(d),
            reviewsTotal = total,
            sections = sections,
            reviews = reviews(d),
        )
    }
}

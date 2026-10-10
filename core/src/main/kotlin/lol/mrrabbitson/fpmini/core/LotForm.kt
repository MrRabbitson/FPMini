package lol.mrrabbitson.fpmini.core

import org.jsoup.nodes.Element

/** Поле формы редактирования лота. kind: text | number | textarea | select | checkbox | hidden */
data class LotField(
    val name: String,
    val kind: String,
    val label: String,
    val value: String,
    val options: List<FieldOption> = emptyList(),
    val hint: String = "",
    val locale: String? = null,           // ru / en для описаний на двух языках
    val conditions: List<Pair<String, List<String>>> = emptyList(), // поле видно, только если другое поле = одно из значений
    val hiddenTwin: Boolean = false,      // у чекбокса есть hidden с тем же именем (браузер шлёт пустое значение)
)

data class LotEditForm(
    val offerId: String,
    val title: String,
    val fields: List<LotField>,
    val csrf: String?,
) {
    /** Видно ли поле при текущих значениях формы. */
    fun isActive(f: LotField, values: Map<String, String>): Boolean =
        f.conditions.all { (other, allowed) -> values[other].orEmpty() in allowed }

    /** Тело запроса offerSave — так же, как его отправляет браузер. */
    fun buildSave(values: Map<String, String>, csrfFallback: String?): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "csrf_token" to (csrf ?: csrfFallback.orEmpty())
        for (f in fields) {
            if (f.name == "csrf_token" || !isActive(f, values)) continue
            val v = values[f.name] ?: f.value
            when (f.kind) {
                "checkbox" -> {
                    if (f.hiddenTwin) out += f.name to ""
                    if (v == "on" || v == "1" || v == "true") out += f.name to "on"
                }
                "number" -> out += f.name to v.replace(',', '.').replace(Regex("[\\s ]"), "")
                else -> out += f.name to v
            }
        }
        return out
    }
}

object LotFormParser {

    private fun conditions(form: Element): Map<String, List<Pair<String, List<String>>>> {
        val out = mutableMapOf<String, List<Pair<String, List<String>>>>()
        form.select("div.lot-fields[data-fields]").forEach { box ->
            val arr = MiniJson.parse(box.attr("data-fields")) as? List<*> ?: return@forEach
            arr.forEach { o ->
                val obj = o as? Map<*, *> ?: return@forEach
                val id = obj["id"]?.toString() ?: return@forEach
                val conds = (obj["conditions"] as? List<*>)?.mapNotNull { c ->
                    val cm = c as? Map<*, *> ?: return@mapNotNull null
                    val other = cm["id"]?.toString() ?: return@mapNotNull null
                    val list = (cm["list"] as? List<*>)?.map { it.toString() }.orEmpty()
                    "fields[$other]" to list
                }.orEmpty()
                if (conds.isNotEmpty()) out[id] = conds
            }
        }
        return out
    }

    /** Разбор страницы /lots/offerEdit. Если формы нет — FunPay показал ошибку, её текст в исключении. */
    fun parse(html: String, offerId: String): LotEditForm {
        val d = Parser.doc(html)
        val form = d.selectFirst("form.form-offer-editor")
            ?: throw IllegalStateException(
                d.selectFirst(".page-content .alert, .alert-danger, p.lead")?.text()?.trim()?.ifBlank { null }
                    ?: "Форма редактирования не загрузилась"
            )
        val conds = conditions(form)
        val fields = linkedMapOf<String, LotField>()
        for (el in form.select("input[name], textarea[name], select[name]")) {
            val name = el.attr("name")
            if (name.isBlank() || el.hasAttr("disabled")) continue
            val group = el.parents().firstOrNull { it.hasClass("form-group") || it.hasClass("lot-field") }
            val fieldId = el.parents().firstOrNull { it.hasClass("lot-field") && it.hasAttr("data-id") }?.attr("data-id")
            val label = ((group?.selectFirst("label.control-label") ?: group?.selectFirst("label"))?.text()?.trim().orEmpty())
                .ifBlank { name }
            val hint = group?.selectFirst(".help-block")?.text()?.trim().orEmpty()
            val locale = group?.attr("data-locale")?.ifBlank { null }
                ?: when { name.endsWith("[ru]") -> "ru"; name.endsWith("[en]") -> "en"; else -> null }
            val c = fieldId?.let { conds[it] }.orEmpty()
            when (el.tagName()) {
                "textarea" -> fields[name] = LotField(name, "textarea", label, el.wholeText(), hint = hint, locale = locale, conditions = c)
                "select" -> fields[name] = LotField(
                    name, "select", label,
                    el.selectFirst("option[selected]")?.attr("value") ?: el.selectFirst("option")?.attr("value").orEmpty(),
                    el.select("option").map { FieldOption(it.attr("value"), it.text().replace(' ', ' ').trim()) },
                    hint, locale, c,
                )
                else -> when (val type = el.attr("type").lowercase().ifBlank { "text" }) {
                    "submit", "button", "file" -> Unit
                    "hidden" -> {
                        // hidden-двойник чекбокса учитываем у самого чекбокса
                        if (form.select("input[type=checkbox][name='$name']").isEmpty() && name !in fields)
                            fields[name] = LotField(name, "hidden", name, el.attr("value"), conditions = c)
                    }
                    "checkbox" -> {
                        val twin = form.select("input[type=hidden][name='$name']").isNotEmpty()
                        val cbLabel = el.parent()?.text()?.trim()?.ifBlank { null } ?: label
                        fields[name] = LotField(name, "checkbox", cbLabel, if (el.hasAttr("checked")) "on" else "", hint = hint, conditions = c, hiddenTwin = twin)
                    }
                    "radio" -> {
                        val opt = FieldOption(el.attr("value"), el.parent()?.text()?.trim()?.ifBlank { null } ?: el.attr("value"))
                        val prev = fields[name]
                        val checked = if (el.hasAttr("checked")) el.attr("value") else null
                        fields[name] = if (prev == null) LotField(name, "select", label, checked.orEmpty(), listOf(opt), hint, locale, c)
                        else prev.copy(options = prev.options + opt, value = checked ?: prev.value)
                    }
                    else -> {
                        val number = type == "number" || el.attr("inputmode") == "decimal" || name == "price" || name == "amount"
                        fields[name] = LotField(name, if (number) "number" else "text", label, el.attr("value"), hint = hint, locale = locale, conditions = c)
                    }
                }
            }
        }
        val csrf = form.selectFirst("input[name=csrf_token]")?.attr("value") ?: Parser.appData(d).csrfToken
        if (fields["offer_id"] == null && offerId != "0") fields["offer_id"] = LotField("offer_id", "hidden", "offer_id", offerId)
        val title = d.selectFirst("h1")?.text()?.trim().orEmpty()
        return LotEditForm(offerId, title, fields.values.toList(), csrf)
    }
}

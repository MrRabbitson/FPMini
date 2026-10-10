package lol.mrrabbitson.fpmini.core

/** Диалог в списке чатов. */
data class ChatPreview(
    val node: String,          // id чата (data-id)
    val name: String,
    val avatarUrl: String?,
    val lastMessage: String,
    val time: String,
    val unread: Boolean,
    val lastMsgId: Long?,      // data-node-msg — меняется, когда приходит новое сообщение
)

data class ChatMessage(
    val id: Long,
    val authorId: Int?,        // 0 — системные сообщения FunPay
    val authorName: String,
    val text: String,
    val time: String,
    val imageUrl: String?,
    val links: List<MsgLink> = emptyList(),   // ссылки внутри текста (ник, заказ, адреса)
)

data class MsgLink(val text: String, val url: String)

data class ChatPage(
    val node: String,
    val interlocutor: UserShort?,
    val messages: List<ChatMessage>,
)

enum class OrderStatus { PAID, CLOSED, REFUND }

data class Order(
    val id: String,
    val description: String,
    val category: String,      // "Discord, Услуги"
    val buyer: UserShort?,
    val priceText: String,
    val priceValue: Double?,
    val date: String,
    val status: OrderStatus,
    val statusText: String,
)

data class Ticket(
    val id: String,
    val title: String,
    val status: String,
    val statusKind: String,    // open | waiting | solved | closed | other
    val date: String,
)

data class TicketComment(
    val author: String,
    val text: String,
    val time: String,
)

data class TicketPage(
    val id: String,
    val title: String,
    val status: String?,
    val comments: List<TicketComment>,
    val replyToken: String?,
)

/** Раздел с моими лотами — для поднятия нужен id игры. */
data class MyLotGroup(
    val nodeId: Int,
    val type: String,          // lots | chips
    val title: String,
    val gameId: Int?,
    val offers: List<OfferRow>,
)

/** Страница заказа. */
data class OrderPage(
    val id: String,
    val status: String?,
    val params: List<Param>,
    val buyer: UserShort?,
    val chatNode: String?,
    val canRefund: Boolean,
)

data class TicketCategory(val id: String, val name: String)

data class FieldOption(val value: String, val label: String)

/** Поле формы новой заявки. kind: text | textarea | select | radio */
data class TicketField(
    val name: String,
    val label: String,
    val kind: String,
    val required: Boolean,
    val options: List<FieldOption>,
)

data class TicketForm(val categoryId: String, val fields: List<TicketField>, val token: String?)

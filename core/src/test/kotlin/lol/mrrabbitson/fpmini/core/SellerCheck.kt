package lol.mrrabbitson.fpmini.core

// Проверка разбора страниц продавца на образцах разметки (запуск: main).

private fun ok(cond: Boolean, msg: String) { if (!cond) error("FAIL: $msg") else println("ok  $msg") }

private val CHATS = """
<div class="contact-list">
 <a href="https://funpay.com/chat/?node=123456" class="contact-item unread" data-id="123456" data-node-msg="9001" data-user-msg="9000">
  <div class="contact-item-photo"><div class="avatar-photo" style="background-image: url(https://sfunpay.com/s/avatar/b.jpg);"></div></div>
  <div class="media-user-name">Buyer1</div>
  <div class="contact-item-message">Здравствуйте, есть в наличии?</div>
  <div class="contact-item-time">14:02</div>
 </a>
 <a href="https://funpay.com/chat/?node=777" class="contact-item" data-id="777" data-node-msg="50">
  <div class="media-user-name">Buyer2</div>
  <div class="contact-item-message">спасибо</div>
  <div class="contact-item-time">вчера</div>
 </a>
</div>
"""

private val CHAT = """
<html><body>
<div class="chat-header"><div class="media media-user"><div class="media-left"><div class="avatar-photo" style="background-image:url(/img/a.png)"></div></div>
 <div class="media-body"><div class="media-user-name"><a href="https://funpay.com/users/42/">Buyer1</a></div><div class="media-user-status">Онлайн</div></div></div></div>
<div class="chat-message-list">
 <div class="chat-msg-item chat-msg-with-head" id="message-100">
  <div class="media-heading"><a href="https://funpay.com/users/42/" class="chat-msg-author-link">Buyer1</a><div class="chat-msg-date" title="10 октября, 14:01:55">14:01</div></div>
  <div class="chat-msg-body"><div class="chat-msg-text">Привет<br>есть?</div></div>
 </div>
 <div class="chat-msg-item" id="message-101"><div class="chat-msg-body"><div class="chat-msg-text">ещё вопрос</div></div></div>
 <div class="chat-msg-item chat-msg-with-head" id="message-102">
  <div class="media-heading"><a href="https://funpay.com/users/7/" class="chat-msg-author-link">Me</a><div class="chat-msg-date" title="10 октября, 14:03:00">14:03</div></div>
  <div class="chat-msg-body"><div class="chat-msg-text">Да</div></div>
 </div>
 <div class="chat-msg-item chat-msg-with-head" id="message-103">
  <div class="media-heading"><div class="media-user-name">FunPay <span class="label">оповещение</span></div></div>
  <div class="chat-msg-body"><div class="chat-msg-text">Покупатель оплатил заказ #ABC123.</div></div>
 </div>
 <div class="chat-msg-item" id="message-104"><a class="chat-img-link" href="/img/chat/1.png"><img src="/img/chat/1_s.png"></a></div>
</div></body></html>
"""

private val ORDERS = """
<html><body><div class="orders-table">
 <a href="https://funpay.com/orders/ABC123/" class="tc-item info">
  <div class="tc-date-time">Сегодня, 14:00</div>
  <div class="tc-order">#ABC123</div>
  <div class="order-desc"><div>Участники 100 шт.</div><div class="text-muted">Discord, Услуги</div></div>
  <div class="tc-user"><div class="media media-user"><div class="avatar-photo" data-href="https://funpay.com/users/42/" style="background-image:url(/img/a.png)"></div>
   <div class="media-user-name"><span class="pseudo-a" data-href="https://funpay.com/users/42/">Buyer1</span></div></div></div>
  <div class="tc-status text-primary">Оплачен</div>
  <div class="tc-price text-nowrap tc-seller-sum">3.50 <span class="unit">₽</span></div>
 </a>
 <a href="https://funpay.com/orders/XYZ9/" class="tc-item">
  <div class="order-desc"><div>Хостинг</div><div class="text-muted">Discord, Прочее</div></div>
  <div class="tc-status text-success">Закрыт</div><div class="tc-price">6.44 ₽</div>
 </a>
 <a href="https://funpay.com/orders/R1/" class="tc-item warning"><div class="order-desc"><div>X</div></div><div class="tc-status">Возврат</div><div class="tc-price">1 ₽</div></a>
</div></body></html>
"""

private val MY_PROFILE = """
<html><body>
<div class="offer">
 <div class="offer-list-title-container"><div class="offer-list-title"><h3><a href="https://funpay.com/lots/102/">Услуги Discord</a></h3></div></div>
 <a class="tc-item" href="https://funpay.com/lots/offer?id=1"><div class="tc-desc-text">Лот 1</div><div class="tc-price" data-s="1">1 ₽</div></a>
</div>
<div class="offer">
 <div class="offer-list-title-container" data-game-id="99"><div class="offer-list-title"><h3><a href="https://funpay.com/chips/5/">Золото</a></h3></div></div>
 <a class="tc-item" href="https://funpay.com/chips/offer?id=55-5"><div class="tc-desc-text">Золото</div><div class="tc-price">2 ₽</div></a>
</div>
</body></html>
"""

private val TICKETS = """
<html><body>
<a class="ticket-item" href="/tickets/555">
 <div class="row"><div class="col-12 mt-2">Не пришли деньги</div><span class="badge bg-warning">Ожидает ответа</span><div class="text-secondary">9 окт</div></div>
</a>
<a class="ticket-item" href="https://support.funpay.com/tickets/556/"><div class="col-12">Вопрос</div><span class="badge bg-success">Решена</span></a>
<ul><li><a class="page-link" rel="next" href="?page=2">2</a></li></ul>
</body></html>
"""

private val TICKET = """
<html><body>
<ol class="breadcrumb"><li class="breadcrumb-item active">Заявка #555 Не пришли деньги</li></ol>
<div class="ticket-info-panel"><span class="badge bg-warning">Ожидает ответа</span></div>
<div class="ticket-comment"><div class="comment-username"><span class="username">Я</span><span>9 окт, 12:00</span></div><div class="comment-text"><p>Здравствуйте</p><p>Деньги не пришли</p></div></div>
<div class="ticket-comment"><div class="comment-username"><span class="username">Агент</span><span>9 окт, 13:00</span></div><div class="comment-text">Проверяем</div></div>
<form><input type="hidden" name="add_comment[_token]" value="TOKEN1"></form>
</body></html>
"""

fun main() {
    val chats = SellerParser.chatList(CHATS)
    ok(chats.size == 2, "чаты: 2 диалога")
    ok(chats[0].node == "123456" && chats[0].unread && chats[0].lastMsgId == 9001L, "чаты: id/непрочитано/последнее")
    ok(chats[0].avatarUrl == "https://sfunpay.com/s/avatar/b.jpg" && chats[0].name == "Buyer1", "чаты: имя/аватар")
    ok(!chats[1].unread && chats[1].time == "вчера", "чаты: прочитанный")

    val chat = SellerParser.chat(CHAT, "123456")
    ok(chat.interlocutor?.id == 42 && chat.interlocutor?.isOnline == true, "чат: собеседник")
    ok(chat.messages.size == 5, "чат: 5 сообщений")
    ok(chat.messages[0].text == "Привет\nесть?" && chat.messages[0].authorId == 42, "чат: текст/автор")
    ok(chat.messages[1].authorId == 42 && chat.messages[1].time == "10 октября, 14:01:55", "чат: продолжение группы")
    ok(chat.messages[2].authorId == 7, "чат: моё сообщение")
    ok(chat.messages[3].authorId == 0 && chat.messages[3].authorName == "FunPay", "чат: системное")
    ok(chat.messages[4].imageUrl == "https://funpay.com/img/chat/1.png", "чат: картинка")

    val orders = SellerParser.orders(ORDERS)
    ok(orders.size == 3, "продажи: 3 заказа")
    ok(orders[0].id == "ABC123" && orders[0].status == OrderStatus.PAID && orders[0].priceValue == 3.5, "продажи: оплачен")
    ok(orders[0].description == "Участники 100 шт." && orders[0].category == "Discord, Услуги", "продажи: описание")
    ok(orders[0].buyer?.id == 42 && orders[0].buyer?.name == "Buyer1", "продажи: покупатель")
    ok(orders[1].status == OrderStatus.CLOSED && orders[2].status == OrderStatus.REFUND, "продажи: статусы")

    val home = HomePage(listOf(Game(10, "Discord", listOf(CategoryRef(102, "lots", "Услуги")))), null, null)
    val lots = SellerParser.myLots(MY_PROFILE, SellerParser.nodeToGame(home))
    ok(lots.size == 2 && lots[0].gameId == 10 && lots[0].offers.size == 1, "мои лоты: игра из главной")
    ok(lots[1].type == "chips" && lots[1].gameId == 99, "мои лоты: игра из data-game-id")

    val t = SellerParser.tickets(TICKETS)
    ok(t.size == 2 && t[0].id == "555" && t[0].statusKind == "waiting" && t[0].title == "Не пришли деньги", "заявки: список")
    ok(t[1].id == "556" && t[1].statusKind == "solved", "заявки: решена")
    ok(SellerParser.hasNextTicketsPage(TICKETS), "заявки: следующая страница")
    val tp = SellerParser.ticket(TICKET, "555")
    ok(tp.title == "Не пришли деньги" && tp.comments.size == 2 && tp.replyToken == "TOKEN1", "заявка: детали")
    ok(tp.comments[0].text == "Здравствуйте\nДеньги не пришли" && tp.comments[1].time == "9 окт, 13:00", "заявка: комментарии")
    println("ALL OK")
    mainExtra()
}

private val ORDER = """
<html><body>
<h1 class="page-header">Заказ #ABC123 <span class="text-primary">Оплачен</span></h1>
<div class="param-item"><h5>Игра</h5><div>Discord</div></div>
<div class="param-item"><h5>Краткое описание</h5><div>Участники 100 шт.</div></div>
<div class="param-item"><h5>Сумма</h5><div>3.50 ₽</div></div>
<button class="btn btn-default btn-refund">Вернуть деньги</button>
<div class="chat" data-id="123456" data-name="users-7-42">
 <div class="chat-header"><div class="media"><div class="media-body"><div class="media-user-name"><a href="https://funpay.com/users/42/">Buyer1</a></div><div class="media-user-status">Онлайн</div></div></div></div>
</div>
</body></html>
"""

private val NEW_TICKET = """
<html><body><select id="ticket_select_form"><option value="">Выберите вариант...</option><option value="1">Проблема с заказом</option><option value="2">Вопрос по выплате</option></select></body></html>
"""

private val TICKET_FORM = """
<html><body><form>
<div class="mb-3"><label for="f1" class="required">Номер заказа *</label><input id="f1" type="text" name="ticket[fields][1]"></div>
<fieldset><legend>Ваша роль *</legend>
 <input type="radio" id="r1" name="ticket[fields][2]" value="1"><label for="r1">Продавец</label>
 <input type="radio" id="r2" name="ticket[fields][2]" value="2"><label for="r2">Покупатель</label>
</fieldset>
<div class="mb-3"><label for="s1">Игра</label><select id="s1" name="ticket[fields][3]"><option value="">Выберите</option><option value="10">Discord</option></select></div>
<div class="mb-3"><label>Сообщение</label><textarea name="ticket[comment][body_html]"></textarea></div>
<input type="hidden" name="ticket[_token]" value="TK">
</form></body></html>
"""

fun mainExtra() {
    val o = SellerParser.order(ORDER, "ABC123")
    ok(o.params.size == 3 && o.params[2].value == "3.50 ₽", "заказ: параметры")
    ok(o.buyer?.id == 42 && o.chatNode == "123456" && o.canRefund, "заказ: покупатель/чат/возврат")
    ok(o.status == "Оплачен", "заказ: статус ${o.status}")
    val cats = SellerParser.ticketCategories(NEW_TICKET)
    ok(cats.map { it.id } == listOf("1", "2"), "новая заявка: категории")
    val f = SellerParser.ticketForm(TICKET_FORM, "1")
    ok(f.token == "TK" && f.fields.size == 4, "форма: ${f.fields.map { it.label + "/" + it.kind }}")
    ok(f.fields[0].label == "Номер заказа" && f.fields[0].required, "форма: текстовое обязательное")
    ok(f.fields[1].kind == "radio" && f.fields[1].options.map { it.label } == listOf("Продавец", "Покупатель") && f.fields[1].label == "Ваша роль", "форма: радио")
    ok(f.fields[2].kind == "select" && f.fields[2].options.single().label == "Discord", "форма: список")
    println("EXTRA OK")
}

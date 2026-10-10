package app.yarmarka.core

// Простая проверка разбора на фикстурах (запуск: main). В проекте не участвует в сборке APK.

private fun check(cond: Boolean, msg: String) { if (!cond) error("FAIL: $msg") else println("ok  $msg") }

private val HOME = """
<html><body data-app-data='{"locale":"ru","csrf-token":"abc123","userId":555}'>
<div class="user-link-dropdown"><span class="user-link-name">Tester</span></div>
<div class="promo-games-list">
 <div class="promo-game-item">
  <div class="game-title" data-id="1"><a href="https://funpay.com/lots/100/">Discord</a></div>
  <ul class="list-inline" data-id="1">
   <li><a href="https://funpay.com/lots/101/">Серверы</a></li>
   <li><a href="https://funpay.com/lots/102/">Услуги</a></li>
   <li><a href="https://funpay.com/chips/7/">Nitro</a></li>
  </ul>
 </div>
 <div class="promo-game-item">
  <div class="game-title" data-id="2"><a href="/lots/200/">Abyss of Dungeons</a></div>
  <ul class="list-inline" data-id="2"><li><a href="/lots/201/">Аккаунты</a></li></ul>
  <ul class="list-inline" data-id="3"><li><a href="/lots/301/">Другой регион</a></li></ul>
 </div>
</div></body></html>
"""

private val CATEGORY = """
<html><body>
<div class="cd-wide-bg" style="background-image: url(/img/layout/cover.jpg)"></div>
<h1>Услуги Discord</h1>
<div class="counter-list">
 <a class="counter-item active" href="https://funpay.com/lots/102/"><div class="counter-param">Услуги</div><div class="counter-value">1 234</div></a>
 <a class="counter-item" href="https://funpay.com/lots/101/"><div class="counter-param">Серверы</div><div class="counter-value">56</div></a>
</div>
<a class="tc-item" href="https://funpay.com/lots/offer?id=64588663">
  <div class="tc-desc"><div class="tc-desc-text">【АВТОВЫДАЧА 24/7】 Участники</div></div>
  <div class="tc-user"><div class="media media-user online"><div class="media-left"><div class="avatar-photo" data-href="https://funpay.com/users/16485541/" style="background-image: url(https://sfunpay.com/s/avatar/a.jpg);"></div></div>
   <div class="media-body"><div class="media-user-name">MrRabbitson</div><div class="media-user-status">Онлайн</div></div></div></div>
  <div class="tc-price" data-s="0.035">0.035 <span class="unit">₽</span></div>
</a>
<a class="tc-item" href="https://funpay.com/lots/offer?id=2"><div class="tc-desc-text">Второй</div><div class="tc-price" data-s="1 170.5">1 170.50 ₽</div></a>
</body></html>
"""

private val OFFER = """
<html><body data-app-data='{"csrf-token":"tok","userId":0}'>
<div class="cd-wide-bg" style="background-image: url('https://funpay.com/img/cover.jpg')"></div>
<a class="js-back-link" href="https://funpay.com/lots/102/">Услуги Discord</a>
<div class="param-list">
 <div class="row">
  <div class="col-xs-6"><div class="param-item"><h5>Тип услуги</h5><div class="text-bold">Участники</div></div></div>
  <div class="col-xs-6"><div class="param-item"><h5>Тип участника</h5><div class="text-bold">Оффлайн участник</div></div></div>
 </div>
 <div class="param-item"><h5>Наличие</h5><div class="text-bold">143 148 шт.</div></div>
 <div class="param-item"><h5>Краткое описание</h5><div>【АВТОВЫДАЧА 24/7】</div></div>
 <div class="param-item"><h5>Подробное описание</h5><div>Строка 1<br>Строка 2<br><br>— Условия —</div></div>
</div>
<div class="chat chat-float"><div class="chat-header"><div class="media media-user online">
 <div class="media-left"><a href="/users/16485541/"><div class="avatar-photo" style="background-image: url(https://sfunpay.com/s/avatar/a.jpg);"></div></a></div>
 <div class="media-body"><div class="media-user-name"><a href="https://funpay.com/users/16485541/">MrRabbitson</a></div><div class="media-user-status">Онлайн</div></div>
</div></div></div>
<select name="method" class="form-control">
 <option value="">Не выбран</option>
 <option value="21" data-content="&lt;span class='payment-title'&gt;Банковская карта RUB&lt;/span&gt;&lt;span class='payment-value'&gt;0.037 ₽&lt;/span&gt;">Банковская карта RUB</option>
 <option value="35">СБП</option>
</select>
<div class="rating-value"><span class="big">4.9</span><span>из 5</span></div>
<div class="rating-full">
 <div class="rating-full-item"><div class="rating-progress"><div class="progress-bar" style="width: 96%"></div></div></div>
 <div class="rating-full-item"><div class="rating-progress"><div class="progress-bar" style="width: 2%"></div></div></div>
 <div class="rating-full-item"><div class="rating-progress"><div class="progress-bar" style="width: 1%"></div></div></div>
 <div class="rating-full-item"><div class="rating-progress"><div class="progress-bar" style="width: 0%"></div></div></div>
 <div class="rating-full-item"><div class="rating-progress"><div class="progress-bar" style="width: 1%"></div></div></div>
</div>
<div class="rating-full-count">2945 отзывов за 1 год</div>
<div class="review-container">
 <div class="review-item-photo"><img src="/img/layout/avatar.png"></div>
 <div class="review-item-date">В этом месяце</div>
 <div class="review-item-detail">Discord, 10 ₽</div>
 <div class="review-item-rating"><div class="rating"><div class="rating5"></div></div></div>
 <div class="review-item-text">спс</div>
 <div class="review-item-answer"><div>Спасибо за покупку!</div></div>
</div>
</body></html>
"""

private val PROFILE = """
<html><body>
<div class="profile-header">
 <div class="profile-header-bg" style="background-image: url(/img/cover2.jpg)"></div>
 <div class="profile-photo" style="background-image: url(https://sfunpay.com/s/avatar/a.jpg)"></div>
 <h1 class="mb40"><span class="mr4">MrRabbitson</span></h1>
 <div class="media-user-status">Онлайн</div>
</div>
<div class="param-item"><h5>Дата регистрации</h5><div class="text-nowrap">5 сентября 2025, 5:25<br>год назад</div></div>
<div class="rating-value"><span class="big">4.9</span></div>
<div class="rating-full-count"><a href="#reviews">Всего 2945 отзывов</a></div>
<div class="offer">
 <div class="offer-list-title-container"><div class="offer-list-title"><h3><a href="https://funpay.com/lots/102/">Услуги Discord</a></h3></div></div>
 <a class="tc-item" href="https://funpay.com/lots/offer?id=64588663"><div class="tc-desc-text">Участники</div><div class="tc-price" data-s="0.035">0.035 ₽</div></a>
 <a class="tc-item" href="https://funpay.com/lots/offer?id=64588664"><div class="tc-desc-text">Хостинг</div><div class="tc-price" data-s="6.44">6.44 ₽</div></a>
</div>
</body></html>
"""

fun main() {
    val h = Parser.home(HOME)
    check(h.games.size == 2, "главная: 2 игры")
    check(h.games[0].categories.map { it.name } == listOf("Серверы", "Услуги", "Nitro"), "главная: разделы Discord")
    check(h.games[0].categories[2].type == "chips", "главная: chips-раздел")
    check(h.games[1].categories.single().id == 201, "главная: берём только свой регион")
    check(h.myUserId == 555 && h.myName == "Tester", "главная: текущий пользователь")

    val c = Parser.category(CATEGORY, 102, "lots")
    check(c.title == "Услуги Discord", "раздел: заголовок")
    check(c.coverUrl == "https://funpay.com/img/layout/cover.jpg", "раздел: обложка")
    check(c.siblings.size == 2 && c.siblings[0].active && c.siblings[0].count == "1 234", "раздел: вкладки")
    check(c.offers.size == 2 && c.offers[0].id == "64588663", "раздел: лоты")
    check(c.offers[0].seller?.name == "MrRabbitson" && c.offers[0].seller?.isOnline == true, "раздел: продавец")
    check(c.offers[0].seller?.id == 16485541, "раздел: id продавца")
    check(c.offers[1].priceValue == 1170.5, "раздел: цена с пробелами")

    val o = Parser.offer(OFFER, "64588663")
    check(o.categoryTitle == "Услуги Discord" && o.categoryRef?.id == 102, "лот: раздел")
    check(o.params.size == 5, "лот: 5 параметров, получили ${o.params.map { it.label }}")
    check(!o.params[0].isWide && o.params[4].isWide, "лот: широкие поля")
    check(o.params[4].value == "Строка 1\nСтрока 2\n\n— Условия —", "лот: переносы: ${o.params[4].value}")
    check(o.seller?.id == 16485541 && o.seller?.avatarUrl == "https://sfunpay.com/s/avatar/a.jpg", "лот: продавец")
    check(o.methods.size == 2 && o.methods[0].title == "Банковская карта RUB" && o.methods[0].unitPrice == 0.037, "лот: способы оплаты")
    check(o.unitLabel == "шт.", "лот: единица")
    check(o.rating?.value == "4.9" && o.rating?.distribution?.size == 5, "лот: рейтинг")
    check(o.reviews.size == 1 && o.reviews[0].stars == 5 && o.reviews[0].answer == "Спасибо за покупку!", "лот: отзыв")
    check(o.coverUrl == "https://funpay.com/img/cover.jpg", "лот: обложка")

    val p = Parser.profile(PROFILE, 16485541)
    check(p.user.name == "MrRabbitson" && p.user.isOnline, "профиль: имя/статус")
    check(p.registered == "5 сентября 2025, 5:25\nгод назад", "профиль: регистрация: ${p.registered}")
    check(p.coverUrl == "https://funpay.com/img/cover2.jpg", "профиль: обложка ${p.coverUrl}")
    check(p.user.avatarUrl == "https://sfunpay.com/s/avatar/a.jpg", "профиль: аватар")
    check(p.reviewsTotal == "Всего 2945 отзывов", "профиль: всего отзывов")
    check(p.sections.size == 1 && p.sections[0].offers.size == 2 && p.sections[0].ref?.id == 102, "профиль: предложения")

    check(Parser.appData(Parser.doc(HOME)).csrfToken == "abc123", "app-data: csrf")
    check(Parser.appData(Parser.doc(OFFER)).userId == null, "app-data: гость")
    println("ALL OK")
}

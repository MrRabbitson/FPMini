package app.yarmarka.core

/** Ссылка на раздел игры: /lots/123/ или /chips/45/ */
data class CategoryRef(
    val id: Int,
    val type: String,      // "lots" | "chips"
    val name: String,
)

data class Game(
    val id: Int,
    val name: String,
    val categories: List<CategoryRef>,
)

data class HomePage(
    val games: List<Game>,
    val myUserId: Int?,
    val myName: String?,
)

data class UserShort(
    val id: Int?,
    val name: String,
    val avatarUrl: String?,
    val status: String?,          // "Онлайн", "Был 5 минут назад" и т.п.
) {
    val isOnline: Boolean get() = status?.contains("онлайн", ignoreCase = true) == true
}

data class OfferRow(
    val id: String,               // id лота (из ?id=)
    val type: String,             // "lots" | "chips"
    val description: String,
    val server: String?,
    val amount: String?,
    val priceText: String,        // "0.035 ₽"
    val priceValue: Double?,      // для сортировки
    val seller: UserShort?,
)

data class CategorySibling(
    val id: Int,
    val type: String,
    val name: String,
    val count: String?,
    val active: Boolean,
)

data class CategoryPage(
    val id: Int,
    val type: String,
    val title: String,
    val coverUrl: String?,
    val siblings: List<CategorySibling>,
    val offers: List<OfferRow>,
)

data class Param(val label: String, val value: String) {
    /** Длинные поля (описания) показываем на всю ширину. */
    val isWide: Boolean
        get() = label.contains("описан", ignoreCase = true) || value.length > 40 || value.contains('\n')
}

data class PaymentMethod(
    val id: String,
    val title: String,
    val priceText: String?,       // "от 0.035 ₽"
    val unitPrice: Double?,
)

data class Rating(
    val value: String?,           // "4.9"
    val countText: String?,       // "2945 отзывов за 1 год"
    val distribution: List<Float>, // 5★..1★, доли 0..1 (может быть пустым)
)

data class Review(
    val authorAvatar: String?,
    val date: String?,
    val detail: String?,          // "Discord, 10 ₽"
    val stars: Int?,
    val text: String,
    val answer: String?,
)

data class OfferPage(
    val id: String,
    val categoryTitle: String?,
    val categoryRef: CategoryRef?,
    val coverUrl: String?,
    val params: List<Param>,
    val seller: UserShort?,
    val methods: List<PaymentMethod>,
    val unitLabel: String?,       // "шт."
    val rating: Rating?,
    val reviews: List<Review>,
)

data class ProfileSection(
    val title: String,
    val ref: CategoryRef?,
    val offers: List<OfferRow>,
)

data class ProfilePage(
    val id: Int,
    val user: UserShort,
    val coverUrl: String?,
    val registered: String?,      // "5 сентября 2025, 5:25\nгод назад"
    val rating: Rating?,
    val reviewsTotal: String?,    // "Всего 2945 отзывов"
    val sections: List<ProfileSection>,
    val reviews: List<Review>,
)

data class AppData(val userId: Int?, val csrfToken: String?)

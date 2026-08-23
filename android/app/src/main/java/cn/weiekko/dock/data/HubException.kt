package cn.weiekko.dock.data

class HubException(
    val code: String,
    override val message: String,
    val httpStatus: Int? = null,
) : Exception(message) {
    val isUnauthorized: Boolean get() = code == "unauthorized" || httpStatus == 401
    val isLoginRequired: Boolean get() = code == "login_required"
}

class HubNetworkException(override val message: String, cause: Throwable? = null) : Exception(message, cause)

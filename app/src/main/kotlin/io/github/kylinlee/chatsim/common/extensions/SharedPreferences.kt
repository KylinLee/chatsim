package io.github.kylinlee.chatsim.common.extensions

import android.content.SharedPreferences
import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.data.model.PhoneAccountHandleModel
import kotlinx.serialization.json.Json

private val phoneAccountJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun SharedPreferences.Editor.putPhoneAccountHandle(
    key: String,
    parcelable: PhoneAccountHandle
): SharedPreferences.Editor {
    val componentName = parcelable.componentName
    val myPhoneAccountHandleModel = PhoneAccountHandleModel(
        componentName.packageName, componentName.className, parcelable.id
    )
    return putString(key, phoneAccountJson.encodeToString(PhoneAccountHandleModel.serializer(), myPhoneAccountHandleModel))
}

/** 旧数据是 Gson 写的同名字段 JSON，kotlinx 可直接读取。 */
fun SharedPreferences.getPhoneAccountHandleModel(
    key: String,
    default: PhoneAccountHandleModel?
): PhoneAccountHandleModel? {
    val raw = getString(key, null) ?: return default
    return runCatching { phoneAccountJson.decodeFromString(PhoneAccountHandleModel.serializer(), raw) }.getOrDefault(default)
}

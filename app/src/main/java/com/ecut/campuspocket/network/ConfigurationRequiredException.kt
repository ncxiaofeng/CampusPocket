package com.ecut.campuspocket.network

import java.io.IOException

/** 缺少运行期第三方参数（电费 operatorPwd / U净 HMAC / 慧生活设备盐）。 */
class ConfigurationRequiredException(
    message: String
) : IOException(message)

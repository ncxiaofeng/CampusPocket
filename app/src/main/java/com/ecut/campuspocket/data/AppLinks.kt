package com.ecut.campuspocket.data

/**
 * 项目对外地址。发版时只需要改这里。
 */
object AppLinks {

    const val GITHUB_REPO = "https://github.com/ncxiaofeng/CampusPocket"
    const val GITHUB_RELEASES = "https://github.com/ncxiaofeng/CampusPocket/releases"
    const val GITHUB_LATEST_RELEASE_API =
        "https://api.github.com/repos/ncxiaofeng/CampusPocket/releases/latest"

    /**
     * 国内无法访问 GitHub 时的蓝奏云镜像（固定文件夹分享）。
     *
     * 免费账号不支持直接分享 APK，所以只提供 ZIP（内含 APK、SHA256 与安装说明）。
     * 发版时把新 ZIP 上传并移动到该文件夹即可，链接和提取码都不变。
     */
    const val LANZOU_FOLDER = "https://ncxiaofeng.lanzoub.com/b0j1u822d"
    const val LANZOU_FOLDER_PWD = "c3bc"
}

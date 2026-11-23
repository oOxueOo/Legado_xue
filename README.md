# 阅读X

<div align="center">
<img width="125" height="125" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="阅读X"/>
<br>
阅读X
<br>
继承自 阅读Max，自己修改自用
</div>

## 关于本项目

- 本仓库是 [Legado_Max](https://github.com/Suml-1/Legado_Max) 的**个人自用改版**，由 [oOxueOo](https://github.com/oOxueOo) 维护，仅供自己使用。
- 更上游是 [gedoor/Legado](https://github.com/gedoor/Legado)。
- 不做功能推广，也不承诺维护与技术支持；代码质量与兼容性按个人使用场景为准。
- 仓库内保留了 MAX 与 Legado 的全部开源许可与依赖说明，感谢上游。

## 本版改动

- 应用名统一改为「阅读X」，桌面图标重做。
- **应用主题包扩展**：在原有「配色 + 顶栏 + 底栏 + 封面图集」之外，新增
  ① 启动页背景 ② 阅读页背景 ③ 默认封面（书封） ④ 阅读排版，导入后可一键全部应用。
- 主题包里的默认封面只写入「默认封面」入口，不再自动占用「封面图集」，设置里不会多出多余的书封图集组。
- 版本号固定为 `3.26X1`，Release 资产文件名带 `legado_xue_` 前缀。

## 版本与安装

- 本仓库产出 **appMax release**：包名 `io.legado.app.yuedu.release`
- 与旧包包名一致，**可覆盖升级、保留数据**（升级前仍建议自行备份）
- Release 资产命名：`legado_xue_beta_3.26X1_release_<abi>.apk`
- 按设备架构下载即可，`arm64-v8a` 为主流机型

## API

- 阅读3.0 提供了2种方式的API：`Web方式`和`Content Provider方式`。您可以在[这里](api.md)根据需要自行调用。
- 可通过url唤起阅读进行一键导入,url格式: legado://import/{path}?src={url}
- path类型: bookSource,rssSource,replaceRule,textTocRule,httpTTS,theme,readConfig,dictRule,[addToBookshelf](/app/src/main/java/io/legado/app/ui/association/AddToBookshelfDialog.kt)
- path类型解释: 书源,订阅源,替换规则,本地txt小说目录规则,在线朗读引擎,主题,阅读排版,添加到书架

## Other

##### 免责声明

https://gedoor.github.io/Disclaimer

##### 阅读3.0

- [书源规则](https://mgz0227.github.io/The-tutorial-of-Legado/)
- [更新日志](/app/src/main/assets/updateLog.md)
- [帮助文档](/app/src/main/assets/web/help/md/appHelp.md)
- [web端书架](https://github.com/gedoor/legado_web_bookshelf)
- [web端源编辑](https://github.com/gedoor/legado_web_source_editor)

## Grateful-感谢

> - org.jsoup:jsoup
> - cn.wanghaomiao:JsoupXpath
> - com.jayway.jsonpath:json-path
> - com.github.gedoor:rhino-android
> - org.htmlunit:htmlunit-core-js（Rhino 的 HtmlUnit 分支，Legado 定制构建）
> - com.squareup.okhttp3:okhttp
> - com.github.bumptech.glide:glide
> - org.nanohttpd:nanohttpd
> - org.nanohttpd:nanohttpd-websocket
> - cn.bingoogolapple:bga-qrcode-zxing
> - com.jaredrummler:colorpicker
> - org.apache.commons:commons-text
> - io.noties.markwon:core
> - io.noties.markwon:image-glide
> - com.hankcs:hanlp
> - com.positiondev.epublib:epublib-core
> - com.github.Moriafly:LyricViewX
> - io.github.rosemoe:editor

感谢 MAX：[Suml-1/Legado_Max](https://github.com/Suml-1/Legado_Max)

更上游：[gedoor/Legado](https://github.com/gedoor/Legado)

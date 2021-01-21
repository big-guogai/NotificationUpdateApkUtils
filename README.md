# apk更新下载FileDownloader相关处理以及更新进度状态栏相关处理

本人是个Android菜鸟，本项目旨在相关知识存档，以便后期使用

工具类文件名NotificationUpdateApkUtils.java

使用到了第三方工具类UpdateAppUtils（https://github.com/teprinciple/UpdateAppUtils），使用方法请看官方文档

这个工具呢可以直接实现下载更新弹窗等操作，不过没给出自定义通知栏的接口，我也是用到后面才发现
因为懒得改了所以就用的它的弹窗写的，其实可以完全自己做弹窗哈（我弄复杂了有点）
第三方工具类FileDownloader（https://github.com/lingochamp/FileDownloader），使用方法请看官方文档
这个工具主要实现了下载功能，非常好用，实现了断点续传功能

代码呢我贴出来了，直接下载或者复制到你的java文件里，建议仅做参考，我也是半罐水
当前更新工具类需要在application的oncreate方法中实例化

    public static NotificationUpdateApkUtils notificationUpdateApkUtils;
    notificationUpdateApkUtils = new NotificationUpdateApkUtils(this);
    
在需要更新的地方引用updata方法

    MyApplication.notificationUpdateApkUtils.updata();
    
代码里写了获取通知权限

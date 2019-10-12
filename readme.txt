Android 4.0版本上编译
将这Android.mk和samplewindow.java两个文件放在$TOP/frameworks/base/cmds/samplewindow/
下，然后用make或mm命令进行编译。最终生成的结果是samplewindow.jar，文件位置在out/target/<
ProductName>/system/framework/下。将该文件通过adb push到手机的/system/framework/下。

然而，samplewindow.jar不是一个可执行程序，。故需借助Android的app_process工具通过samplewindow脚本来加载并执行它。

app_process其实就是大名鼎鼎的zygote。不过，只有使用--zygote参数启动时它才会给改名为zygote[2]，否则就像java –jar命令一样，运行指定类的main静态函数。

在手机中执行该脚本，其运行结果是一个灰色的方块不断地从屏幕左侧移动到右侧
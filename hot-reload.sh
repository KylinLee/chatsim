#!/bin/sh

echo "🚀 [Hot Reload] 正在监听代码变更..."

# 使用 inotifywait 监听 /app 目录下的文件修改事件
while inotifywait -r -e modify,create,delete --exclude 'build/|.gradle/' /app; do
    echo "[Hot Reload] 检测到代码变更，开始编译..."
    
    # 执行增量编译并自动安装到模拟器（通过 adb 桥接）
    # -x 表示跳过 lint 检查以加快速度
    ./gradlew installDebug -x lint 
    
    if [ $? -eq 0 ]; then
        echo "[Hot Reload] 编译安装成功！正在重启应用..."
        # 获取包名并重启主 Activity (这里假设包名为 com.example.myapp，请根据实际情况修改)
        adb shell am start -n com.example.myapp/.MainActivity
    else
        echo "[Hot Reload] 编译失败，请检查控制台报错！"
    fi
done

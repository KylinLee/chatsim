// Windows 控制台默认按 GBK 输出，pwsh 读取/打印中文时会乱码。
// 在所有 bash 工具命令前追加 UTF-8 输出编码设置，避免中文乱码。
export const PowershellUtf8Plugin = async () => ({
  "tool.execute.before": async (input, output) => {
    if (process.platform !== "win32" || input.tool !== "bash") return
    const command = output.args?.command
    if (typeof command !== "string") return
    if (command.includes("[Console]::OutputEncoding")) return
    output.args.command = "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; " + command
  },
})

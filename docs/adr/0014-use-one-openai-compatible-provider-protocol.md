# 首版只实现 OpenAI-compatible Provider 协议

首版 BYOK 提供 DeepSeek、OpenAI 和 OpenRouter 预设，并允许用户配置 Base URL、API Key、模型名与必要的额外请求头；所有预设共享一套 OpenAI-compatible 客户端。Gemini、Anthropic 等原生协议进入后续路线图，避免在首版为品牌差异维护多套请求、流式响应和错误处理逻辑。

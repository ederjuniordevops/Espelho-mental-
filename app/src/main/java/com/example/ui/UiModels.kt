package com.example.ui

data class Challenge(
    val id: String,
    val emoji: String,
    val label: String
)

data class DecisionStyle(
    val id: String,
    val label: String
)

data class SessionTopic(
    val id: String,
    val emoji: String,
    val label: String,
    val cat: String // "deep", "healing", "practical"
)

data class ReadingTopic(
    val id: String,
    val emoji: String,
    val label: String
)

object StaticData {
    val CHALLENGES = listOf(
        Challenge("anxiety", "😰", "Ansiedade"),
        Challenge("relationships", "❤️", "Relacionamentos"),
        Challenge("decisions", "🧭", "Tomada de decisão"),
        Challenge("purpose", "✨", "Propósito de vida"),
        Challenge("work", "💼", "Trabalho / Carreira"),
        Challenge("selfesteem", "🪞", "Autoestima"),
        Challenge("family", "👨‍👩‍👧", "Família"),
        Challenge("loneliness", "🌙", "Solidão"),
        Challenge("stress", "🔥", "Estresse / Burnout"),
        Challenge("grief", "💔", "Luto / Perda"),
        Challenge("procrastination", "⏰", "Procrastinação"),
        Challenge("identity", "🧩", "Identidade"),
        Challenge("socialanxiety", "😶", "Ansiedade social"),
        Challenge("financialstress", "💸", "Estresse financeiro"),
        Challenge("transitions", "🌀", "Transições de vida"),
        Challenge("depression", "🌧️", "Depressão"),
        Challenge("anger", "😤", "Raiva / Irritabilidade"),
        Challenge("worklife", "⚖️", "Equilíbrio trabalho-vida")
    )

    val DECISION_STYLES = listOf(
        DecisionStyle("analytical", "Analiso tudo antes de decidir"),
        DecisionStyle("intuitive", "Confio muito no instinto"),
        DecisionStyle("avoidant", "Fico travado, evito decidir"),
        DecisionStyle("impulsive", "Decido rápido e depois me arrependo")
    )

    val SESSION_TOPICS = listOf(
        SessionTopic("fear", "😨", "Confrontar medos", "deep"),
        SessionTopic("grief", "💔", "Processar uma perda", "deep"),
        SessionTopic("anxiety", "😰", "Ansiedade crônica", "deep"),
        SessionTopic("loneliness", "🌙", "Solidão profunda", "deep"),
        SessionTopic("purpose", "🧭", "Falta de propósito", "deep"),
        SessionTopic("depression", "🌧️", "Atravessando depressão", "deep"),
        SessionTopic("transitions", "🌀", "Transições de vida", "deep"),
        SessionTopic("self-worth", "💎", "Valor próprio", "healing"),
        SessionTopic("anger", "🔥", "Raiva e frustração", "healing"),
        SessionTopic("relationships", "❤️", "Padrões em relacionamentos", "healing"),
        SessionTopic("socialanxiety", "😶", "Ansiedade social", "healing"),
        SessionTopic("boundaries", "🛡️", "Estabelecer limites", "practical"),
        SessionTopic("procrastination", "⏰", "Procrastinação", "practical"),
        SessionTopic("imposter", "🎭", "Síndrome do impostor", "practical"),
        SessionTopic("perfectionism", "⭕", "Perfeccionismo", "practical"),
        SessionTopic("goals", "🎯", "Definir e alcançar metas", "practical"),
        SessionTopic("habits", "🔄", "Formação de hábitos", "practical"),
        SessionTopic("communication", "🗣️", "Comunicação e relacionamentos", "practical")
    )

    val READING_TOPICS = listOf(
        ReadingTopic("procrastination", "⏰", "Procrastinação"),
        ReadingTopic("boundaries", "🛡️", "Limites saudáveis"),
        ReadingTopic("imposter", "🎭", "Síndrome do impostor"),
        ReadingTopic("attachment", "🔗", "Estilo de apego"),
        ReadingTopic("perfectionism", "⭕", "Perfeccionismo"),
        ReadingTopic("loneliness", "🌙", "Solidão"),
        ReadingTopic("purpose", "🧭", "Propósito de vida"),
        ReadingTopic("anxiety", "😰", "Ansiedade"),
        ReadingTopic("grief", "💔", "Luto e perda"),
        ReadingTopic("selfworth", "💎", "Valor próprio"),
        ReadingTopic("habits", "🔄", "Formação de hábitos"),
        ReadingTopic("goals", "🎯", "Definição de metas"),
        ReadingTopic("communication", "🗣️", "Comunicação"),
        ReadingTopic("resilience", "🌱", "Resiliência"),
        ReadingTopic("mindfulness", "🧘", "Mindfulness"),
        ReadingTopic("leadership", "👑", "Liderança"),
        ReadingTopic("creativity", "🎨", "Criatividade"),
        ReadingTopic("financialstress", "💸", "Estresse financeiro")
    )
}

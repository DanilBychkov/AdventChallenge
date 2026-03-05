package org.bothubclient.presentation.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.bothubclient.domain.entity.*
import org.bothubclient.presentation.ui.components.DropdownSelector

private enum class ProfileTab(val title: String) {
    Identity("Identity"),
    Preferences("Preferences"),
    Rules("Rules"),
    Context("Context"),
    Invariants("Invariants")
}

@Composable
fun ProfileEditorDialog(
    profile: UserProfile,
    isSaving: Boolean,
    error: String?,
    onSave: (UserProfile) -> Unit,
    onClose: () -> Unit
) {
    val dialogState =
        rememberDialogState(
            width = 1100.dp,
            height = 820.dp
        )

    DialogWindow(
        onCloseRequest = onClose,
        state = dialogState,
        title = "User Profile",
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            ProfileEditorScreen(
                profile = profile,
                isSaving = isSaving,
                error = error,
                onSave = onSave,
                onClose = onClose
            )
        }
    }
}

@Composable
fun ProfileEditorScreen(
    profile: UserProfile,
    isSaving: Boolean,
    error: String?,
    onSave: (UserProfile) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ProfileTab.Identity) }
    var edited by remember(profile.id) { mutableStateOf(profile) }
    var preferredTechnologiesText by remember(profile.id) {
        mutableStateOf(preferredTechnologiesToText(profile.context.preferredTechnologies))
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "Профиль", style = MaterialTheme.typography.h6)
            IconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!error.isNullOrBlank()) {
            Text(text = error, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.body2)
            Spacer(modifier = Modifier.height(8.dp))
        }

        TabRow(selectedTabIndex = selectedTab.ordinal) {
            ProfileTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        val scrollState = rememberScrollState()
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    ProfileTab.Identity -> {
                        OutlinedTextField(
                            value = edited.name,
                            onValueChange = { edited = edited.copy(name = it) },
                            label = { Text("Название профиля") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edited.identity.displayName,
                            onValueChange = {
                                edited = edited.copy(identity = edited.identity.copy(displayName = it))
                            },
                            label = { Text("Как обращаться к пользователю") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edited.identity.role,
                            onValueChange = {
                                edited = edited.copy(identity = edited.identity.copy(role = it))
                            },
                            label = { Text("Роль/профессия") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        StringListEditor(
                            title = "Области экспертизы",
                            items = edited.identity.expertiseAreas,
                            onChange = {
                                edited = edited.copy(identity = edited.identity.copy(expertiseAreas = it))
                            }
                        )
                    }

                    ProfileTab.Preferences -> {
                        CommunicationStyleEditor(
                            communicationStyle = edited.preferences.communicationStyle,
                            onChange = {
                                edited = edited.copy(preferences = edited.preferences.copy(communicationStyle = it))
                            }
                        )
                        ResponseFormatEditor(
                            responseFormat = edited.preferences.responseFormat,
                            onChange = {
                                edited = edited.copy(preferences = edited.preferences.copy(responseFormat = it))
                            }
                        )
                        LanguagePrefsEditor(
                            languagePrefs = edited.preferences.language,
                            onChange = {
                                edited = edited.copy(preferences = edited.preferences.copy(language = it))
                            }
                        )
                        TechnicalLevelEditor(
                            technicalLevel = edited.preferences.technicalLevel,
                            onChange = {
                                edited = edited.copy(preferences = edited.preferences.copy(technicalLevel = it))
                            }
                        )
                    }

                    ProfileTab.Rules -> {
                        BehaviorRulesEditor(
                            rules = edited.behaviorRules,
                            onChange = { edited = edited.copy(behaviorRules = it) }
                        )
                    }

                    ProfileTab.Context -> {
                        OutlinedTextField(
                            value = edited.context.projectContext,
                            onValueChange = {
                                edited = edited.copy(context = edited.context.copy(projectContext = it))
                            },
                            label = { Text("Контекст проекта") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = edited.context.companyContext,
                            onValueChange = {
                                edited = edited.copy(context = edited.context.copy(companyContext = it))
                            },
                            label = { Text("Контекст компании") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        StringListEditor(
                            title = "Текущие цели",
                            items = edited.context.currentGoals,
                            onChange = { edited = edited.copy(context = edited.context.copy(currentGoals = it)) }
                        )
                        StringListEditor(
                            title = "Избегать тем",
                            items = edited.context.avoidedTopics,
                            onChange = { edited = edited.copy(context = edited.context.copy(avoidedTopics = it)) }
                        )
                        OutlinedTextField(
                            value = preferredTechnologiesText,
                            onValueChange = { preferredTechnologiesText = it },
                            label = { Text("Предпочтительные технологии (категория: tech1, tech2)") },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                            maxLines = 8
                        )
                    }

                    ProfileTab.Invariants -> {
                        InvariantsEditor(
                            invariants = edited.invariants,
                            onChange = { edited = edited.copy(invariants = it) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose, enabled = !isSaving) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val parsedPreferredTechnologies = parsePreferredTechnologies(preferredTechnologiesText)
                    onSave(edited.copy(context = edited.context.copy(preferredTechnologies = parsedPreferredTechnologies)))
                },
                enabled = !isSaving
            ) {
                Text(if (isSaving) "Saving..." else "Save")
            }
        }
    }
}

@Composable
private fun CommunicationStyleEditor(
    communicationStyle: CommunicationStyle,
    onChange: (CommunicationStyle) -> Unit
) {
    Text("Стиль общения", style = MaterialTheme.typography.subtitle1)

    var formalityExpanded by remember { mutableStateOf(false) }
    DropdownSelector(
        label = "Формальность",
        selectedValue = communicationStyle.formality,
        displayValue = { it.name },
        items = FormalityLevel.entries,
        onSelected = { onChange(communicationStyle.copy(formality = it)) },
        expanded = formalityExpanded,
        onExpandedChange = { formalityExpanded = it },
        modifier = Modifier.fillMaxWidth()
    )

    var verbosityExpanded by remember { mutableStateOf(false) }
    DropdownSelector(
        label = "Подробность",
        selectedValue = communicationStyle.verbosity,
        displayValue = { it.name },
        items = VerbosityLevel.entries,
        onSelected = { onChange(communicationStyle.copy(verbosity = it)) },
        expanded = verbosityExpanded,
        onExpandedChange = { verbosityExpanded = it },
        modifier = Modifier.fillMaxWidth()
    )

    var toneExpanded by remember { mutableStateOf(false) }
    DropdownSelector(
        label = "Тон",
        selectedValue = communicationStyle.tone,
        displayValue = { it.name },
        items = ToneStyle.entries,
        onSelected = { onChange(communicationStyle.copy(tone = it)) },
        expanded = toneExpanded,
        onExpandedChange = { toneExpanded = it },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ResponseFormatEditor(
    responseFormat: ResponseFormat,
    onChange: (ResponseFormat) -> Unit
) {
    Text("Формат ответа", style = MaterialTheme.typography.subtitle1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = responseFormat.useMarkdown,
            onCheckedChange = { onChange(responseFormat.copy(useMarkdown = it)) }
        )
        Text("Использовать Markdown")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = responseFormat.preferLists,
            onCheckedChange = { onChange(responseFormat.copy(preferLists = it)) }
        )
        Text("Предпочитать списки")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = responseFormat.includeSummaries,
            onCheckedChange = { onChange(responseFormat.copy(includeSummaries = it)) }
        )
        Text("Добавлять итоги")
    }

    var codeStyleExpanded by remember { mutableStateOf(false) }
    DropdownSelector(
        label = "Код-блоки",
        selectedValue = responseFormat.codeBlockStyle,
        displayValue = { it.name },
        items = CodeBlockStyle.entries,
        onSelected = { onChange(responseFormat.copy(codeBlockStyle = it)) },
        expanded = codeStyleExpanded,
        onExpandedChange = { codeStyleExpanded = it },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LanguagePrefsEditor(
    languagePrefs: LanguagePrefs,
    onChange: (LanguagePrefs) -> Unit
) {
    Text("Язык", style = MaterialTheme.typography.subtitle1)
    OutlinedTextField(
        value = languagePrefs.primaryLanguage,
        onValueChange = { onChange(languagePrefs.copy(primaryLanguage = it)) },
        label = { Text("Основной язык (например, ru/en)") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = languagePrefs.codeCommentsLanguage,
        onValueChange = { onChange(languagePrefs.copy(codeCommentsLanguage = it)) },
        label = { Text("Язык комментариев в коде") },
        modifier = Modifier.fillMaxWidth()
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = languagePrefs.translateTechnicalTerms,
            onCheckedChange = { onChange(languagePrefs.copy(translateTechnicalTerms = it)) }
        )
        Text("Переводить технические термины")
    }
}

@Composable
private fun TechnicalLevelEditor(
    technicalLevel: TechnicalLevel,
    onChange: (TechnicalLevel) -> Unit
) {
    Text("Уровень экспертизы", style = MaterialTheme.typography.subtitle1)
    var expanded by remember { mutableStateOf(false) }
    DropdownSelector(
        label = "Уровень",
        selectedValue = technicalLevel,
        displayValue = { it.name },
        items = TechnicalLevel.entries,
        onSelected = onChange,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StringListEditor(
    title: String,
    items: List<String>,
    onChange: (List<String>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.subtitle1)
    var newItem by remember { mutableStateOf("") }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            label = { Text("Добавить") },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = {
                val trimmed = newItem.trim()
                if (trimmed.isNotEmpty()) {
                    onChange(items + trimmed)
                    newItem = ""
                }
            }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить")
        }
    }

    if (items.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }
        }
    }
}

@Composable
private fun BehaviorRulesEditor(
    rules: List<BehaviorRule>,
    onChange: (List<BehaviorRule>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Правила поведения", style = MaterialTheme.typography.subtitle1)
        IconButton(onClick = { onChange(rules + BehaviorRule(condition = "", action = "", priority = 0)) }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить правило")
        }
    }

    if (rules.isEmpty()) {
        Text(text = "Нет правил", style = MaterialTheme.typography.body2, color = Color.Gray)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rules.forEachIndexed { index, rule ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rule.condition,
                        onValueChange = { v ->
                            onChange(
                                rules.mapIndexed { i, r -> if (i == index) r.copy(condition = v) else r }
                            )
                        },
                        label = { Text("Условие (если...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = rule.action,
                        onValueChange = { v ->
                            onChange(
                                rules.mapIndexed { i, r -> if (i == index) r.copy(action = v) else r }
                            )
                        },
                        label = { Text("Действие (то...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val priorityText = remember(rule.priority) { rule.priority.toString() }
                    var priorityField by remember(index) { mutableStateOf(rule.priority.toString()) }
                    if (priorityText != priorityField && priorityField.toIntOrNull() == rule.priority) {
                        priorityField = priorityText
                    }
                    OutlinedTextField(
                        value = priorityField,
                        onValueChange = { v ->
                            priorityField = v
                            val parsed = v.toIntOrNull()
                            if (parsed != null) {
                                onChange(
                                    rules.mapIndexed { i, r -> if (i == index) r.copy(priority = parsed) else r }
                                )
                            }
                        },
                        label = { Text("Приоритет (число)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onChange(rules.filterIndexed { i, _ -> i != index }) }) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvariantsEditor(
    invariants: List<ProfileInvariant>,
    onChange: (List<ProfileInvariant>) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Инварианты", style = MaterialTheme.typography.subtitle1)
        IconButton(
            onClick = {
                onChange(
                    invariants +
                            ProfileInvariant(
                                category = InvariantCategory.TECH_STACK,
                                description = ""
                            )
                )
            }
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить инвариант")
        }
    }

    if (invariants.isEmpty()) {
        Text(text = "Нет инвариантов", style = MaterialTheme.typography.body2, color = Color.Gray)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        invariants.forEachIndexed { index, inv ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                elevation = 1.dp,
                shape = MaterialTheme.shapes.small
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = inv.isActive,
                                onCheckedChange = { checked ->
                                    onChange(
                                        invariants.mapIndexed { i, item ->
                                            if (i == index) item.copy(isActive = checked) else item
                                        }
                                    )
                                }
                            )
                            Text(if (inv.isActive) "Активен" else "Отключён")
                        }
                        TextButton(onClick = { onChange(invariants.filterIndexed { i, _ -> i != index }) }) {
                            Text("Удалить")
                        }
                    }

                    var categoryExpanded by remember(index) { mutableStateOf(false) }
                    DropdownSelector(
                        label = "Категория",
                        selectedValue = inv.category,
                        displayValue = { it.name },
                        items = InvariantCategory.entries,
                        onSelected = { selected ->
                            onChange(
                                invariants.mapIndexed { i, item ->
                                    if (i == index) item.copy(category = selected) else item
                                }
                            )
                        },
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    var severityExpanded by remember(index) { mutableStateOf(false) }
                    DropdownSelector(
                        label = "Серьёзность",
                        selectedValue = inv.severity,
                        displayValue = { it.name },
                        items = InvariantSeverity.entries,
                        onSelected = { selected ->
                            onChange(
                                invariants.mapIndexed { i, item ->
                                    if (i == index) item.copy(severity = selected) else item
                                }
                            )
                        },
                        expanded = severityExpanded,
                        onExpandedChange = { severityExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inv.description,
                        onValueChange = { v ->
                            onChange(
                                invariants.mapIndexed { i, item ->
                                    if (i == index) item.copy(description = v) else item
                                }
                            )
                        },
                        label = { Text("Описание") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = inv.rationale,
                        onValueChange = { v ->
                            onChange(
                                invariants.mapIndexed { i, item ->
                                    if (i == index) item.copy(rationale = v) else item
                                }
                            )
                        },
                        label = { Text("Обоснование") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun preferredTechnologiesToText(map: Map<String, List<String>>): String =
    map.toSortedMap().entries.joinToString("\n") { (k, v) -> "$k: ${v.joinToString(", ")}" }

private fun parsePreferredTechnologies(text: String): Map<String, List<String>> {
    val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
    val result = LinkedHashMap<String, List<String>>()
    lines.forEach { line ->
        val idx = line.indexOf(':')
        if (idx <= 0) return@forEach
        val category = line.substring(0, idx).trim()
        val values =
            line.substring(idx + 1)
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
        if (category.isNotBlank() && values.isNotEmpty()) {
            result[category] = values
        }
    }
    return result
}

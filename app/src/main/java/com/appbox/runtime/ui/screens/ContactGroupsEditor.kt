package com.appbox.runtime.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appbox.runtime.core.model.HoshiContact
import com.appbox.runtime.core.model.HoshiContactGroup
import com.appbox.runtime.service.agent.ContactGroupStore
import com.appbox.runtime.ui.components.AppBoxPanel
import com.appbox.runtime.ui.theme.AppBoxThemeColors

@Composable
fun ContactGroupsEditor(
    groups: List<HoshiContactGroup>,
    defaultGroupId: String,
    onDefaultGroupChange: (String) -> Unit,
    onGroupsChange: (List<HoshiContactGroup>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Groupes de contacts WhatsApp", color = AppBoxThemeColors.TextPrimary, fontSize = 14.sp)

        groups.forEach { group ->
            AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = defaultGroupId == group.id,
                            onClick = { onDefaultGroupChange(group.id) },
                        )
                        OutlinedTextField(
                            value = group.name,
                            onValueChange = { name ->
                                onGroupsChange(groups.map { if (it.id == group.id) it.copy(name = name) else it })
                            },
                            label = { Text("Nom du groupe") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = group.messageTemplate,
                        onValueChange = { tpl ->
                            onGroupsChange(groups.map { if (it.id == group.id) it.copy(messageTemplate = tpl) else it })
                        },
                        label = { Text("Message ({{name}}, {{time}}, {{group}})") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 3,
                    )
                    group.contacts.forEach { contact ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = contact.name,
                                onValueChange = { n ->
                                    updateContact(groups, group.id, contact.id) { it.copy(name = n) }
                                        .let(onGroupsChange)
                                },
                                label = { Text("Contact") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                            OutlinedTextField(
                                value = contact.phone,
                                onValueChange = { p ->
                                    updateContact(groups, group.id, contact.id) { it.copy(phone = p) }
                                        .let(onGroupsChange)
                                },
                                label = { Text("Tél") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppBoxPanel(cornerRadius = 8.dp, modifier = Modifier.clickable {
                            val newContact = ContactGroupStore.newContact("", "+33")
                            onGroupsChange(groups.map {
                                if (it.id == group.id) it.copy(contacts = it.contacts + newContact) else it
                            })
                        }) {
                            Text("+ Contact", modifier = Modifier.padding(8.dp), color = AppBoxThemeColors.Accent, fontSize = 11.sp)
                        }
                        if (groups.size > 1) {
                            AppBoxPanel(cornerRadius = 8.dp, modifier = Modifier.clickable {
                                onGroupsChange(groups.filter { it.id != group.id })
                                if (defaultGroupId == group.id) onDefaultGroupChange("")
                            }) {
                                Text("Supprimer", modifier = Modifier.padding(8.dp), color = AppBoxThemeColors.TextTertiary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        AppBoxPanel(cornerRadius = 10.dp, modifier = Modifier.fillMaxWidth().clickable {
            onGroupsChange(groups + ContactGroupStore.newGroup("Nouveau groupe"))
        }) {
            Text(
                "+ Ajouter un groupe",
                modifier = Modifier.padding(12.dp),
                color = AppBoxThemeColors.Accent,
                fontSize = 12.sp,
            )
        }
    }
}

private fun updateContact(
    groups: List<HoshiContactGroup>,
    groupId: String,
    contactId: String,
    transform: (HoshiContact) -> HoshiContact,
): List<HoshiContactGroup> = groups.map { group ->
    if (group.id != groupId) group
    else group.copy(contacts = group.contacts.map { if (it.id == contactId) transform(it) else it })
}

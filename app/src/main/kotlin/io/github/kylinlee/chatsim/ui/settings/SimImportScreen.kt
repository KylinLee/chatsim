package io.github.kylinlee.chatsim.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.SimImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimImportScreen(
    onBack: () -> Unit,
    viewModel: SimImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            io.github.kylinlee.chatsim.R.string.import_contacts_from_sim
                        )
                    )
                },
                navigationIcon = {
                    if (!listDetailLayout) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(
                        io.github.kylinlee.chatsim.R.string.sim_import_description
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                if (state.isImporting) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.importing))
                } else {
                    Button(onClick = viewModel::import) {
                        Text(
                            stringResource(
                                io.github.kylinlee.chatsim.R.string.start_import
                            )
                        )
                    }
                }

                if (state.isDone) {
                    Spacer(Modifier.height(24.dp))

                    val nothingFound = state.newContacts == 0 &&
                        state.mergedContacts == 0 &&
                        state.addedNumbers == 0

                    if (nothingFound) {
                        Text(
                            text = stringResource(
                                io.github.kylinlee.chatsim.R.string.sim_import_nothing_found
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                ResultRow(
                                    label = stringResource(
                                        io.github.kylinlee.chatsim.R.string.sim_import_new_contacts
                                    ),
                                    value = state.newContacts,
                                )
                                ResultRow(
                                    label = stringResource(
                                        io.github.kylinlee.chatsim.R.string.sim_import_merged_contacts
                                    ),
                                    value = state.mergedContacts,
                                )
                                ResultRow(
                                    label = stringResource(
                                        io.github.kylinlee.chatsim.R.string.sim_import_added_numbers
                                    ),
                                    value = state.addedNumbers,
                                )
                                ResultRow(
                                    label = stringResource(
                                        io.github.kylinlee.chatsim.R.string.sim_import_ignored_records
                                    ),
                                    value = state.ignoredRecords,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: Int,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()
    }
}

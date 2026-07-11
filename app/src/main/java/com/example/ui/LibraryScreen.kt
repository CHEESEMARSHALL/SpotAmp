package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.PlexMetadata

@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    onNavigateToPlaylist: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Artists", "Albums", "Playlists")

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }

    LaunchedEffect(selectedSectionId) {
        if (selectedSectionId.isNotEmpty()) {
            viewModel.loadArtists()
            viewModel.loadAlbums()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp)
    ) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (selectedSectionId.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No music library selected. Choose a library in Settings.",
                    style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.5f))
                )
            }
            return
        }

        // Custom M3 Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = {
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            },
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { 
                        Text(
                            text = title, 
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp
                        ) 
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        if (isLoading && artists.isEmpty() && albums.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> { // Artists Grid
                        if (artists.isEmpty()) {
                            EmptyLibraryState("No artists found in this library.")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(artists) { artist ->
                                    ArtistGridItem(
                                        artist = artist, 
                                        baseUrl = viewModel.repository.settings.baseUrl, 
                                        token = viewModel.repository.settings.token,
                                        onMoreClick = {
                                            activeContextMenu = ContextMenuItem.Artist(
                                                ratingKey = artist.ratingKey,
                                                name = artist.title,
                                                thumb = artist.thumb ?: ""
                                            )
                                        }
                                    ) {
                                        onNavigateToArtist(artist.ratingKey, artist.title)
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Albums Grid
                        if (albums.isEmpty()) {
                            EmptyLibraryState("No albums found in this library.")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(albums) { album ->
                                    AlbumGridItem(
                                        album = album, 
                                        baseUrl = viewModel.repository.settings.baseUrl, 
                                        token = viewModel.repository.settings.token,
                                        onMoreClick = {
                                            activeContextMenu = ContextMenuItem.Album(
                                                ratingKey = album.ratingKey,
                                                title = album.title,
                                                artist = album.parentTitle ?: "Unknown Artist",
                                                thumb = album.thumb ?: ""
                                            )
                                        }
                                    ) {
                                        onNavigateToAlbum(album.ratingKey, album.title)
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Playlists Dashboard (Gemini framework + lists)
                        val playlists by viewModel.playlists.collectAsStateWithLifecycle()
                        val aiLoading by viewModel.aiLoading.collectAsStateWithLifecycle()
                        val aiError by viewModel.aiError.collectAsStateWithLifecycle()
                        
                        var playlistPrompt by remember { mutableStateOf("") }
                        var showCreateDialog by remember { mutableStateOf(false) }
                        var manualPlaylistName by remember { mutableStateOf("") }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Gemini AI Playlist Generator Framework Section
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.AutoAwesome,
                                                contentDescription = "Gemini",
                                                tint = Color(0xFF818CF8),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "Gemini AI Playlist Generator",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }

                                        Text(
                                            text = "Describe the vibe, genre, or mood, and Gemini will synthesize a smart playlist from your catalog sample.",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color.White.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )

                                        OutlinedTextField(
                                            value = playlistPrompt,
                                            onValueChange = { playlistPrompt = it },
                                            placeholder = { Text("e.g. Chill synthwave vibes for night driving", color = Color.White.copy(alpha = 0.3f)) },
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF6366F1).copy(alpha = 0.5f),
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.05f),
                                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ai_playlist_prompt_input"),
                                            singleLine = true,
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = !aiLoading
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        if (aiLoading) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFF6366F1),
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    "Gemini is curation-synthesizing tracks...",
                                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.7f))
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    if (playlistPrompt.isNotBlank()) {
                                                        viewModel.generateAiPlaylist(playlistPrompt)
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("ai_playlist_generate_button"),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF6366F1)
                                                ),
                                                enabled = playlistPrompt.isNotBlank()
                                            ) {
                                                Text("Synthesize AI Playlist", fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        aiError?.let { err ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = err,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.fillMaxWidth(),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Saved Playlists List Header
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Saved Playlists (${playlists.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )

                                    TextButton(
                                        onClick = { showCreateDialog = true },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF818CF8))
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Playlist")
                                    }
                                }
                            }

                            if (playlists.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No playlists created yet. Tap '+' to create one manually or try Gemini!",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White.copy(alpha = 0.4f),
                                                textAlign = TextAlign.Center
                                            )
                                        )
                                    }
                                }
                            } else {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onNavigateToPlaylist(playlist.id, playlist.name) }
                                            .background(Color.White.copy(alpha = 0.02f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
                                                    ),
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                            Text(
                                                text = "Local Playlist",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Create Manual Playlist Dialog
                        if (showCreateDialog) {
                            AlertDialog(
                                onDismissRequest = { showCreateDialog = false },
                                title = { Text("New Playlist", color = Color.White) },
                                containerColor = Color(0xFF1E1E24),
                                text = {
                                    OutlinedTextField(
                                        value = manualPlaylistName,
                                        onValueChange = { manualPlaylistName = it },
                                        placeholder = { Text("Playlist name", color = Color.White.copy(alpha = 0.4f)) },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (manualPlaylistName.isNotBlank()) {
                                                viewModel.createPlaylist(manualPlaylistName)
                                                manualPlaylistName = ""
                                                showCreateDialog = false
                                            }
                                        },
                                        enabled = manualPlaylistName.isNotBlank()
                                    ) {
                                        Text("Create")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCreateDialog = false }) {
                                        Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Context Menu Trigger
    activeContextMenu?.let { item ->
        MusicContextMenu(
            item = item,
            viewModel = viewModel,
            onDismiss = { activeContextMenu = null },
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToAlbum = onNavigateToAlbum
        )
    }
}

@Composable
fun ArtistGridItem(
    artist: PlexMetadata,
    baseUrl: String,
    token: String,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!artist.thumb.isNullOrEmpty()) "$normalizedBaseUrl${artist.thumb}?X-Plex-Token=$token" else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("artist_item_${artist.ratingKey}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                // Circular portrait for Artists
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(60.dp) // Circular
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = artist.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }

                // More Overlay
                IconButton(
                    onClick = { onMoreClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = artist.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun EmptyLibraryState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
    }
}

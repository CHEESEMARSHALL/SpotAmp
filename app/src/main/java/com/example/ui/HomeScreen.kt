package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.data.*
import com.example.playback.TrackItem

@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToArtist: (String, String) -> Unit,
    onNavigateToAlbum: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val selectedLibraryName by viewModel.selectedLibraryName.collectAsStateWithLifecycle()
    val homeFeedState by viewModel.homeFeedState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var activeContextMenu by remember { mutableStateOf<ContextMenuItem?>(null) }
    val baseUrl = viewModel.repository.settings.baseUrl
    val token = viewModel.repository.settings.token

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_feed"),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // 1. Personalized Header Row
            item {
                HomeHeader(
                    libraryName = selectedLibraryName,
                    isConfigured = isConfigured,
                    onNavigateToSettings = onNavigateToSettings
                )
            }

            // Connection Check Banner (if Plex/Backend URL is unconfigured)
            if (!isConfigured) {
                item {
                    UnconfiguredBanner(onNavigateToSettings = onNavigateToSettings)
                }
            }

            // Skeleton Loading State
            if (isLoading && homeFeedState.recentPlays.isEmpty()) {
                item {
                    HomeSkeletonLoader()
                }
            } else {
                // SECTION 1: RECENT PLAYS (Dynamic Horizontal Cards)
                if (homeFeedState.recentPlays.isNotEmpty()) {
                    item {
                        RecentPlaysSection(
                            recentPlays = homeFeedState.recentPlays,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = { recent ->
                                if (recent.tracks.isNotEmpty()) {
                                    viewModel.playTrackItem(recent.tracks.first(), recent.tracks)
                                }
                            },
                            onMoreClick = { recent ->
                                if (recent.tracks.isNotEmpty()) {
                                    val track = recent.tracks.first()
                                    activeContextMenu = ContextMenuItem.Track(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        key = track.key,
                                        thumb = track.thumb,
                                        duration = track.duration
                                    )
                                }
                            }
                        )
                    }
                }

                // SECTION 2: RECENTLY ADDED ALBUMS (Horizontally Scrolling Cards)
                if (homeFeedState.recentlyAdded.isNotEmpty()) {
                    item {
                        RecentlyAddedSection(
                            albums = homeFeedState.recentlyAdded,
                            baseUrl = baseUrl,
                            token = token,
                            onAlbumClick = { album ->
                                onNavigateToAlbum(album.ratingKey, album.title)
                            },
                            onMoreClick = { album ->
                                activeContextMenu = ContextMenuItem.Album(
                                    ratingKey = album.ratingKey,
                                    title = album.title,
                                    artist = album.parentTitle ?: "Various Artists",
                                    thumb = album.thumb ?: ""
                                )
                            }
                        )
                    }
                }

                // SECTION 3: DAILY MIXES (Gradients, reasons, clusters)
                if (homeFeedState.dailyMixes.isNotEmpty()) {
                    item {
                        DailyMixesSection(
                            mixes = homeFeedState.dailyMixes,
                            onMixClick = { mix ->
                                viewModel.playDailyMix(mix)
                            }
                        )
                    }
                }

                // SECTION 4: JUMP BACK IN (Quick resume of items)
                if (homeFeedState.jumpBackIn.isNotEmpty()) {
                    item {
                        JumpBackInSection(
                            items = homeFeedState.jumpBackIn,
                            baseUrl = baseUrl,
                            token = token,
                            onItemClick = { item ->
                                when (item.type) {
                                    "album" -> onNavigateToAlbum(item.id.replace("jbi_alb_", ""), item.title)
                                    "artist" -> onNavigateToArtist(item.id.replace("jbi_art_", ""), item.title)
                                    else -> {
                                        // Trigger custom generated radio/mix for playlist/station
                                        viewModel.generateAiPlaylist("Build custom mix for ${item.title}")
                                    }
                                }
                            }
                        )
                    }
                }

                // SECTION 5: RECOMMENDED STATIONS (Radio triggers)
                if (homeFeedState.stations.isNotEmpty()) {
                    item {
                        RecommendedStationsSection(
                            stations = homeFeedState.stations,
                            onStationClick = { station ->
                                viewModel.playStation(station)
                            }
                        )
                    }
                }

                // SECTION 6: MADE FOR YOU (Heavy core / Soundtracks mixes)
                if (homeFeedState.madeForYou.isNotEmpty()) {
                    item {
                        MadeForYouSection(
                            madeForYou = homeFeedState.madeForYou,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = { mfyItem ->
                                if (mfyItem.tracks.isNotEmpty()) {
                                    viewModel.playbackManager.playQueue(mfyItem.tracks, 0)
                                }
                            },
                            onAddQueueClick = { mfyItem ->
                                viewModel.playbackManager.addTracksToQueue(mfyItem.tracks)
                            }
                        )
                    }
                }

                // SECTION 7: MORE FROM [GENRE / COLLECTION]
                if (homeFeedState.moreFromSections.isNotEmpty()) {
                    homeFeedState.moreFromSections.forEach { section ->
                        item {
                            MoreFromSectionLayout(
                                section = section,
                                baseUrl = baseUrl,
                                token = token,
                                onTrackClick = { track ->
                                    viewModel.playTrackItem(track, section.tracks)
                                },
                                onMoreClick = { track ->
                                    activeContextMenu = ContextMenuItem.Track(
                                        ratingKey = track.ratingKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        key = track.key,
                                        thumb = track.thumb,
                                        duration = track.duration
                                    )
                                }
                            )
                        }
                    }
                }

                // SECTION 8: MOST PLAYED THIS MONTH
                if (homeFeedState.mostPlayedThisMonth.isNotEmpty()) {
                    item {
                        MostPlayedSection(
                            items = homeFeedState.mostPlayedThisMonth,
                            baseUrl = baseUrl,
                            token = token,
                            onTrackClick = { trackItem ->
                                viewModel.playTrackItem(trackItem, homeFeedState.mostPlayedThisMonth.map { it.track })
                            },
                            onMoreClick = { trackItem ->
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = trackItem.ratingKey,
                                    title = trackItem.title,
                                    artist = trackItem.artist,
                                    album = trackItem.album,
                                    key = trackItem.key,
                                    thumb = trackItem.thumb,
                                    duration = trackItem.duration
                                )
                            }
                        )
                    }
                }

                // SECTION 9: ON THIS DAY (Anniversaries / Classics throwback)
                homeFeedState.onThisDay?.let { item ->
                    item {
                        OnThisDaySection(
                            onThisDay = item,
                            baseUrl = baseUrl,
                            token = token,
                            onPlayClick = {
                                if (item.tracks.isNotEmpty()) {
                                    viewModel.playbackManager.playQueue(item.tracks, 0)
                                }
                            }
                        )
                    }
                }

                // SECTION 10: NEW RELEASES FOR YOU
                if (homeFeedState.newReleases.isNotEmpty()) {
                    item {
                        NewReleasesSection(
                            releases = homeFeedState.newReleases,
                            baseUrl = baseUrl,
                            token = token,
                            onReleaseClick = { release ->
                                if (release.tracks.isNotEmpty()) {
                                    viewModel.playbackManager.playQueue(release.tracks, 0)
                                }
                            }
                        )
                    }
                }

                // SECTION 11: HISTORY (Recent track list)
                if (homeFeedState.history.isNotEmpty()) {
                    item {
                        HistorySection(
                            history = homeFeedState.history,
                            baseUrl = baseUrl,
                            token = token,
                            onTrackClick = { recent ->
                                viewModel.playRecentTrack(recent)
                            },
                            onMoreClick = { recent ->
                                activeContextMenu = ContextMenuItem.Track(
                                    ratingKey = recent.ratingKey,
                                    title = recent.title,
                                    artist = recent.artist,
                                    album = recent.album,
                                    key = recent.key,
                                    thumb = recent.thumb,
                                    duration = 0L
                                )
                            }
                        )
                    }
                }
            }
        }

        // Context Menu Overlay Sheet
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
}

// ==========================================
// Subcomponents & UI Block Layouts
// ==========================================

@Composable
fun HomeHeader(
    libraryName: String,
    isConfigured: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Dns,
                    contentDescription = "Server Connection",
                    tint = if (isConfigured) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isConfigured) "Connected" else "Offline Mode",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
            Text(
                text = if (libraryName.isNotEmpty()) libraryName else "Plex Audio Library",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Cast/Output visual icon
            IconButton(
                onClick = {},
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = "Cast output",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun UnconfiguredBanner(onNavigateToSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.3f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = "Offline Mode",
                    tint = Color(0xFFFCA5A5)
                )
                Text(
                    text = "Welcome to your Audio Dashboard!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            Text(
                text = "Connect your Plex Media Server in Settings, or index your library to browse cached music while offline.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 20.sp
                )
            )
            Button(
                onClick = onNavigateToSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Configure Connection", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                fontSize = 20.sp,
                letterSpacing = (-0.2).sp
            )
        )
        if (onSeeAllClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onSeeAllClick() }
            ) {
                Text(
                    text = "See all",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF818CF8),
                        fontWeight = FontWeight.Bold
                    )
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "See all",
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 1: RECENT PLAYS
// -------------------------------------------------------------
@Composable
fun RecentPlaysSection(
    recentPlays: List<HomeRecentPlay>,
    baseUrl: String,
    token: String,
    onPlayClick: (HomeRecentPlay) -> Unit,
    onMoreClick: (HomeRecentPlay) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Recent Plays")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(recentPlays) { play ->
                RecentPlayCard(
                    play = play,
                    baseUrl = baseUrl,
                    token = token,
                    onPlayClick = { onPlayClick(play) },
                    onMoreClick = { onMoreClick(play) }
                )
            }
        }
    }
}

@Composable
fun RecentPlayCard(
    play: HomeRecentPlay,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (play.thumb.isNotEmpty()) "$normalizedBaseUrl${play.thumb}?X-Plex-Token=$token" else null

    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable { onPlayClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = play.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF312E81), Color(0xFF4C1D95))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // More Menu Overlay
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Small relative time tag overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = play.relativeTime,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Text(
                text = play.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = play.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.5f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// -------------------------------------------------------------
// SECTION 2: RECENTLY ADDED ALBUMS
// -------------------------------------------------------------
@Composable
fun RecentlyAddedSection(
    albums: List<PlexMetadata>,
    baseUrl: String,
    token: String,
    onAlbumClick: (PlexMetadata) -> Unit,
    onMoreClick: (PlexMetadata) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Recently Added in Music")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums) { album ->
                AlbumCard(
                    album = album,
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onAlbumClick(album) },
                    onMoreClick = { onMoreClick(album) }
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: PlexMetadata,
    baseUrl: String,
    token: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (!album.thumb.isNullOrEmpty()) "$normalizedBaseUrl${album.thumb}?X-Plex-Token=$token" else null

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // More Menu overlay
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }

            // Small year label if available
            album.year?.let { yr ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = yr.toString(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.parentTitle ?: "Various Artists",
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.5f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 3: DAILY MIXES
// -------------------------------------------------------------
@Composable
fun DailyMixesSection(
    mixes: List<DailyMix>,
    onMixClick: (DailyMix) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Daily Mixes")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(mixes) { mix ->
                MixCard(mix = mix, onClick = { onMixClick(mix) })
            }
        }
    }
}

@Composable
fun MixCard(
    mix: DailyMix,
    onClick: () -> Unit
) {
    val gradientColors = mix.colors.map { Color(it) }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = gradientColors))
                .padding(14.dp)
        ) {
            // Background design elements (music discs overlay)
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.05f),
                modifier = Modifier
                    .size(120.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 20.dp, y = 20.dp)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MIX",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play Mix",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = mix.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = mix.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 4: JUMP BACK IN
// -------------------------------------------------------------
@Composable
fun JumpBackInSection(
    items: List<JumpBackInItem>,
    baseUrl: String,
    token: String,
    onItemClick: (JumpBackInItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Jump Back In")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items) { jbi ->
                JumpBackInCard(jbi = jbi, baseUrl = baseUrl, token = token, onClick = { onItemClick(jbi) })
            }
        }
    }
}

@Composable
fun JumpBackInCard(
    jbi: JumpBackInItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (jbi.thumb.isNotEmpty()) "$normalizedBaseUrl${jbi.thumb}?X-Plex-Token=$token" else null

    Column(
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = if (jbi.type == "artist") CircleShape else RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = jbi.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF3F3F46), Color(0xFF18181B))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (jbi.type) {
                                "artist" -> Icons.Rounded.Person
                                "playlist" -> Icons.Rounded.PlaylistPlay
                                else -> Icons.Rounded.Album
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Quick play icon overlay on hover/center
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.15f), if (jbi.type == "artist") CircleShape else RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = jbi.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 12.sp
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = jbi.subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            ),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 5: RECOMMENDED STATIONS
// -------------------------------------------------------------
@Composable
fun RecommendedStationsSection(
    stations: List<RecommendedStation>,
    onStationClick: (RecommendedStation) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Recommended Stations")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(stations) { station ->
                RadioTile(station = station, onClick = { onStationClick(station) })
            }
        }
    }
}

@Composable
fun RadioTile(
    station: RecommendedStation,
    onClick: () -> Unit
) {
    val gradientBrushes = station.gradientColors.map { Color(it) }

    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientBrushes))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Radio,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = station.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = station.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 6: MADE FOR YOU
// -------------------------------------------------------------
@Composable
fun MadeForYouSection(
    madeForYou: List<MadeForYouItem>,
    baseUrl: String,
    token: String,
    onPlayClick: (MadeForYouItem) -> Unit,
    onAddQueueClick: (MadeForYouItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Made For You")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(madeForYou) { mfyItem ->
                LargeRecommendationCard(
                    mfyItem = mfyItem,
                    baseUrl = baseUrl,
                    token = token,
                    onPlayClick = { onPlayClick(mfyItem) },
                    onAddQueueClick = { onAddQueueClick(mfyItem) }
                )
            }
        }
    }
}

@Composable
fun LargeRecommendationCard(
    mfyItem: MadeForYouItem,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit,
    onAddQueueClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (mfyItem.thumb.isNotEmpty()) "$normalizedBaseUrl${mfyItem.thumb}?X-Plex-Token=$token" else null

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Album art cover left
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(120.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = mfyItem.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // Description info right
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = mfyItem.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 15.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = mfyItem.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlayClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onAddQueueClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Queue,
                            contentDescription = "Add to queue",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 7: MORE FROM [GENRE / COLLECTION]
// -------------------------------------------------------------
@Composable
fun MoreFromSectionLayout(
    section: MoreFromSection,
    baseUrl: String,
    token: String,
    onTrackClick: (TrackItem) -> Unit,
    onMoreClick: (TrackItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = section.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(section.tracks) { track ->
                TrackCard(
                    track = track,
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onTrackClick(track) },
                    onMoreClick = { onMoreClick(track) }
                )
            }
        }
    }
}

@Composable
fun TrackCard(
    track: TrackItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (track.thumb.isNotEmpty()) "$normalizedBaseUrl${track.thumb}?X-Plex-Token=$token" else null

    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.5f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 8: MOST PLAYED THIS MONTH
// -------------------------------------------------------------
@Composable
fun MostPlayedSection(
    items: List<MostPlayedItem>,
    baseUrl: String,
    token: String,
    onTrackClick: (TrackItem) -> Unit,
    onMoreClick: (TrackItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "Most Played This Month")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(items) { item ->
                Box(modifier = Modifier.width(140.dp)) {
                    TrackCard(
                        track = item.track,
                        baseUrl = baseUrl,
                        token = token,
                        onClick = { onTrackClick(item.track) },
                        onMoreClick = { onMoreClick(item.track) }
                    )

                    // play counts indicator overlay badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFF818CF8), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${item.playCount} plays",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 9: ON THIS DAY
// -------------------------------------------------------------
@Composable
fun OnThisDaySection(
    onThisDay: OnThisDayItem,
    baseUrl: String,
    token: String,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (onThisDay.thumb.isNotEmpty()) "$normalizedBaseUrl${onThisDay.thumb}?X-Plex-Token=$token" else null

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionHeader(title = "On This Day")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        // Drawing subtle low-opacity throwback gold/brass mesh
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF78350F).copy(alpha = 0.25f), Color(0xFF1E1B4B).copy(alpha = 0.1f))
                            )
                        )
                    }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Album Art
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(130.dp)
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = onThisDay.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFFB45309), Color(0xFF78350F))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        // Years tag badge overlay
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .background(Color(0xFFF59E0B), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = onThisDay.timeAgo,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }

                    // Release Information details
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = onThisDay.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = onThisDay.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFFFBBF24),
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = onThisDay.matchReason,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = onPlayClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Revisit Album", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SECTION 10: NEW RELEASES FOR YOU
// -------------------------------------------------------------
@Composable
fun NewReleasesSection(
    releases: List<NewReleaseItem>,
    baseUrl: String,
    token: String,
    onReleaseClick: (NewReleaseItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "New Releases For You")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(releases) { release ->
                NewReleaseCard(release = release, baseUrl = baseUrl, token = token, onClick = { onReleaseClick(release) })
            }
        }
    }
}

@Composable
fun NewReleaseCard(
    release: NewReleaseItem,
    baseUrl: String,
    token: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (release.thumb.isNotEmpty()) "$normalizedBaseUrl${release.thumb}?X-Plex-Token=$token" else null

    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(bottom = 8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = release.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF047857), Color(0xFF065F46))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FiberNew,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color(0xFF10B981), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }

        Text(
            text = release.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = release.artist,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color.White.copy(alpha = 0.5f)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -------------------------------------------------------------
// SECTION 11: HISTORY
// -------------------------------------------------------------
@Composable
fun HistorySection(
    history: List<RecentTrack>,
    baseUrl: String,
    token: String,
    onTrackClick: (RecentTrack) -> Unit,
    onMoreClick: (RecentTrack) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(title = "History")

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            history.take(6).forEach { recent ->
                RecentHistoryRow(
                    recent = recent,
                    baseUrl = baseUrl,
                    token = token,
                    onClick = { onTrackClick(recent) },
                    onMoreClick = { onMoreClick(recent) }
                )
            }
        }
    }
}

@Composable
fun RecentHistoryRow(
    recent: RecentTrack,
    baseUrl: String,
    token: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    val context = LocalContext.current
    val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
    val imageUrl = if (recent.thumb.isNotEmpty()) "$normalizedBaseUrl${recent.thumb}?X-Plex-Token=$token" else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.02f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = recent.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1F2937)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recent.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = recent.artist,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.5f)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onMoreClick) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More",
                    tint = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SKELETON LOADER
// -------------------------------------------------------------
@Composable
fun HomeSkeletonLoader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                )
            }
        }
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(140.dp)
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                )
            }
        }
    }
}

@Composable
fun AlbumGridItem(
    album: PlexMetadata,
    baseUrl: String,
    token: String,
    onMoreClick: () -> Unit,
    onClick: () -> Unit
) {
    AlbumCard(
        album = album,
        baseUrl = baseUrl,
        token = token,
        onClick = onClick,
        onMoreClick = onMoreClick
    )
}

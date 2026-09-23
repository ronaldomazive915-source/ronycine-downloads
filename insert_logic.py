
import sys

file_path = 'app/src/main/java/com/example/ui/screens/PlayerScreen.kt'

logic = """                             onPlaybackProgress = { currentTime, duration, event ->
                                 if (isValidId && duration > 0) {
                                     currentVideoDuration = duration
                                     val progress = (currentTime / duration).toFloat().coerceIn(0f, 1f)
                                     viewModel.saveWatchProgress(
                                         tmdbId = tmdbId,
                                         mediaType = mediaType,
                                         title = media?.title ?: "Conteúdo RONYCINE",
                                         posterPath = media?.posterPath,
                                         seasonNumber = if (mediaType == "tv" || mediaType == "serie") currentSeasonNum else null,
                                         episodeNumber = if (mediaType == "tv" || mediaType == "serie") currentEpisodeNum else null,
                                         progressPercent = progress,
                                         positionMs = (currentTime * 1000).toLong(),
                                         totalDurationMs = (duration * 1000).toLong()
                                     )

                                     // Smart Next Episode Detection (Netflix-style)
                                     if (mediaType == "tv" || mediaType == "serie") {
                                         val endingWindow = getEpisodeEndingWindow(duration)
                                         val timeRemaining = duration - currentTime
                                         
                                         if (timeRemaining <= endingWindow && timeRemaining > 0) {
                                             if (nextEpisodeTarget == null) {
                                                 nextEpisodeTarget = computeNextEpisodeTarget()
                                             }
                                             
                                             if (nextEpisodeTarget != null) {
                                                 showNextEpisodeButton = true
                                                 remainingSecondsUntilEnd = timeRemaining.toInt()
                                             }
                                         } else if (timeRemaining > endingWindow + 5) {
                                             showNextEpisodeButton = false
                                         }
                                     }
                                 }

                                 if (event == "ended" || event == "completed") {
                                     android.util.Log.i("RONYCINE_PLAYER", "ON_PLAYER_ENDED: mediaType=$mediaType, S$currentSeasonNum E$currentEpisodeNum")
                                     if (mediaType == "tv" || mediaType == "serie") {
                                         val target = nextEpisodeTarget ?: computeNextEpisodeTarget()
                                         if (target != null) {
                                             nextEpisodeTarget = target
                                             if (autoplayEnabled) {
                                                 showNextEpisodeOverlay = false
                                                 showNextEpisodeButton = false
                                                 val nextS = target.seasonNumber
                                                 val nextE = target.episodeNumber
                                                 if (nextS != currentSeasonNum) {
                                                     currentSeasonNum = nextS
                                                     viewModel.loadSeasonEpisodes(tmdbId, nextS)
                                                 }
                                                 currentEpisodeNum = nextE
                                                 playerLoadId++
                                             } else {
                                                 showNextEpisodeOverlay = true
                                             }
                                         } else {
                                             showNextEpisodeOverlay = false
                                         }
                                     }
                                 } else if (event == "playing" || event == "play") {
                                     if (showNextEpisodeOverlay) {
                                         showNextEpisodeOverlay = false
                                     }
                                 }
                             },"""

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
inserted = False
for i, line in enumerate(lines):
    if 'onTryAgain = {' in line and not inserted:
        new_lines.append(logic + '\n')
        inserted = True
    new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)
print("Successfully inserted logic.")

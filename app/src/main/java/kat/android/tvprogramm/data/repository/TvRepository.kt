package kat.android.tvprogramm.data.repository

import kat.android.tvprogramm.data.api.TvApiClient
import kat.android.tvprogramm.data.api.TvApiService
import kat.android.tvprogramm.data.model.ApiShowDto
import kat.android.tvprogramm.data.model.TvCategory
import kat.android.tvprogramm.data.model.TvChannel
import kat.android.tvprogramm.data.model.TvShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed class ApiStatus {
    object Idle : ApiStatus()
    object Loading : ApiStatus()
    data class Success(val sourceInfo: String) : ApiStatus()
    data class Error(val message: String) : ApiStatus()
}

class TvRepository(
    private var apiService: TvApiService = TvApiClient.createService()
) {
    private val _channels = MutableStateFlow<List<TvChannel>>(emptyList())
    val channels: StateFlow<List<TvChannel>> = _channels.asStateFlow()

    private val _shows = MutableStateFlow<List<TvShow>>(emptyList())
    val shows: StateFlow<List<TvShow>> = _shows.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _apiStatus = MutableStateFlow<ApiStatus>(ApiStatus.Idle)
    val apiStatus: StateFlow<ApiStatus> = _apiStatus.asStateFlow()

    private val defaultChannels = listOf(
        TvChannel("ard", "Das Erste (ARD)", "ARD", "Öffentlich-Rechtlich", 1),
        TvChannel("zdf", "ZDF", "ZDF", "Öffentlich-Rechtlich", 2),
        TvChannel("rtl", "RTL", "RTL", "Private", 3),
        TvChannel("sat1", "SAT.1", "SAT1", "Private", 4),
        TvChannel("pro7", "ProSieben", "PRO7", "Private", 5),
        TvChannel("vox", "VOX", "VOX", "Private", 6),
        TvChannel("kabeleins", "Kabel Eins", "K1", "Private", 7),
        TvChannel("arte", "Arte", "ARTE", "Kultur", 8),
        TvChannel("3sat", "3sat", "3SAT", "Kultur", 9),
        TvChannel("wdr", "WDR Fernsehen", "WDR", "Öffentlich-Rechtlich", 10),
        TvChannel("eurosport", "Eurosport 1", "EURO", "Sport", 11),
        TvChannel("kika", "KiKa", "KIKA", "Kinder", 12)
    )

    init {
        _channels.value = defaultChannels
        loadProgramForDate(LocalDate.now())
    }

    suspend fun fetchFromApi(customUrl: String? = null, date: LocalDate = LocalDate.now()): Result<Unit> {
        return withContext(Dispatchers.IO) {
            _apiStatus.value = ApiStatus.Loading
            try {
                if (!customUrl.isNull_or_blank()) {
                    apiService = TvApiClient.createService(customUrl!!)
                }

                val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val response = apiService.getProgramListings(date = dateStr)

                if (response.shows.isNullOrEmpty()) {
                    // Fallback to rich dynamic generator if API payload empty
                    _shows.value = generateMockProgramForDate(date)
                    _apiStatus.value = ApiStatus.Success("Lokale EPG-Daten aktiv (API lieferte leeres Ergebnis)")
                } else {
                    val mappedShows = response.shows.mapNotNull { mapDtoToDomain(it) }
                    _shows.value = mappedShows
                    _apiStatus.value = ApiStatus.Success("Erfolgreich von API geladen ($dateStr)")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                // If API offline or unreachable, fall back gracefully to local schedule generator
                _shows.value = generateMockProgramForDate(date)
                _apiStatus.value = ApiStatus.Error("API nicht erreichbar (${e.localizedMessage ?: "Netzwerkfehler"}). Fallback-Daten geladen.")
                Result.failure(e)
            }
        }
    }

    fun loadProgramForDate(date: LocalDate) {
        _shows.value = generateMockProgramForDate(date)
    }

    fun toggleFavorite(showId: String) {
        val current = _favoriteIds.value.toMutableSet()
        if (current.contains(showId)) {
            current.remove(showId)
        } else {
            current.add(showId)
        }
        _favoriteIds.value = current
    }

    fun getShowById(id: String): TvShow? {
        return _shows.value.find { it.id == id }
    }

    fun getLiveShows(now: LocalDateTime = LocalDateTime.now()): List<TvShow> {
        return _shows.value.filter { it.isLive(now) }
            .sortedBy { getChannelOrder(it.channelId) }
    }

    fun getUpcomingShowsForChannel(channelId: String, limit: Int = 10): List<TvShow> {
        return _shows.value.filter { it.channelId == channelId }
            .sortedBy { it.startTime }
    }

    fun searchShows(query: String): List<TvShow> {
        if (query.isBlank()) return _shows.value
        val lower = query.trim().lowercase()
        return _shows.value.filter { show ->
            show.title.lowercase().contains(lower) ||
            (show.subtitle?.lowercase()?.contains(lower) == true) ||
            show.channelName.lowercase().contains(lower) ||
            show.description.lowercase().contains(lower) ||
            show.category.displayName.lowercase().contains(lower)
        }
    }

    private fun getChannelOrder(channelId: String): Int {
        return defaultChannels.find { it.id == channelId }?.channelNumber ?: 99
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    private fun mapDtoToDomain(dto: ApiShowDto): TvShow? {
        return try {
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val start = LocalDateTime.parse(dto.startTime, formatter)
            val end = LocalDateTime.parse(dto.endTime, formatter)
            val category = when (dto.category?.uppercase()) {
                "MOVIE", "FILM" -> TvCategory.MOVIE
                "SERIES", "SERIE" -> TvCategory.SERIES
                "DOCUMENTARY", "DOKU" -> TvCategory.DOCUMENTARY
                "NEWS", "NACHRICHTEN" -> TvCategory.NEWS
                "SPORT" -> TvCategory.SPORT
                "KIDS", "KINDER" -> TvCategory.KIDS
                else -> TvCategory.ENTERTAINMENT
            }

            TvShow(
                id = dto.id,
                title = dto.title,
                subtitle = dto.subtitle,
                channelId = dto.channelId,
                channelName = dto.channelName,
                startTime = start,
                endTime = end,
                description = dto.description ?: "Keine Detailbeschreibung verfügbar.",
                category = category,
                genre = dto.genre,
                ageRating = dto.ageRating ?: "FSK 12",
                year = dto.year,
                country = dto.country,
                director = dto.director,
                cast = dto.cast ?: emptyList(),
                isHighlight = dto.isHighlight ?: false,
                imageUrl = dto.imageUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun generateMockProgramForDate(date: LocalDate): List<TvShow> {
        val resultList = mutableListOf<TvShow>()
        val now = LocalDateTime.now()

        // Detailed template shows for popular German channels
        val channelTemplates = mapOf(
            "ard" to listOf(
                Triple("05:30", "09:00", TvTemplate("ARD-Morgenmagazin", "Aktuelle News, Sport & Wetter", TvCategory.NEWS, "Nachrichtenmagazin", "FSK 0", "Spannender Start in den Tag mit Live-Gästen, Interviews und den wichtigsten Themen aus Politik, Wirtschaft und Kultur.")),
                Triple("09:00", "09:55", TvTemplate("Meister des Alltags", "Das Wissensquiz mit Florian Weber", TvCategory.ENTERTAINMENT, "Quizshow", "FSK 0", "Kreative Fragen rund um knifflige Alltagssituationen und verblüffende Phänomene.")),
                Triple("10:00", "11:00", TvTemplate("Rote Rosen", "Folge 3920 - Neue Wege", TvCategory.SERIES, "Telenovela", "FSK 6", "Emotionale Verwicklungen und dramatische Wendungen im romantischen Lüneburg.")),
                Triple("11:00", "12:00", TvTemplate("Sturm der Liebe", "Folge 4210 - Gefährliche Liebe", TvCategory.SERIES, "Telenovela", "FSK 6", "Liebe, Intrigen und große Gefühle am Fünf-Sterne-Hotel Fürstenhof.")),
                Triple("12:00", "14:00", TvTemplate("Tagesschau & Mittagsmagazin", "Berichte des Tages aus aller Welt", TvCategory.NEWS, "Nachrichten", "FSK 0", "Umfassende Berichterstattung zu den aktuellen nationalen und internationalen Geschehnissen.")),
                Triple("14:00", "15:00", TvTemplate("Rote Rosen", "Wiederholung vom Vortag", TvCategory.SERIES, "Drama", "FSK 6", "Rolle rückwärts und neue Hoffnung für das junge Glück in der Heidestadt.")),
                Triple("15:00", "16:00", TvTemplate("Gefragt – Gejagt", "Das schnelle Quiz mit Alexander Bommes", TvCategory.ENTERTAINMENT, "Game Show", "FSK 0", "Kandidaten treten gegen erfahrene Quiz-Superhirne ('Jäger') an. Spannung bis zur letzten Sekunde!")),
                Triple("16:00", "17:00", TvTemplate("In aller Freundschaft – Die jungen Ärzte", "Folge 340 - Ausverkauft", TvCategory.SERIES, "Arztserie", "FSK 6", "Dramatische Fälle und persönliche Herausforderungen im Johannes-Thal-Klinikum Erfurt.")),
                Triple("17:00", "18:00", TvTemplate("BRISANT", "Das Boulevardmagazin der ARD", TvCategory.NEWS, "Boulevard", "FSK 6", "Prominente, Schicksale, Trends und emotionale Geschichten hautnah miterlebt.")),
                Triple("18:00", "19:00", TvTemplate("Wer weiß denn sowas?", "Mit Kai Pflaume, Bernhard Hoëcker & Elton", TvCategory.ENTERTAINMENT, "Quiz", "FSK 0", "Verblüffende Wissensfragen und prominente Studiogäste im beliebtesten Vorabend-Quiz.")),
                Triple("19:00", "20:00", TvTemplate("Morde vor der Haustür", "Krimi-Highlights im Norden", TvCategory.SERIES, "Krimi", "FSK 12", "Spannende Ermittlungen mit Humor und Nordlicht-Flair.")),
                Triple("20:00", "20:15", TvTemplate("Tagesschau", "Das Wichtigste vom Tage", TvCategory.NEWS, "Nachrichten", "FSK 0", "Die wichtigsten Nachrichten aus Deutschland und der Welt im Überblick.")),
                Triple("20:15", "21:45", TvTemplate("Tatort: Der dunkle Schatten", "Kriminalfilm mit Thiel & Boerne", TvCategory.SERIES, "Krimi", "FSK 12", "Ein mysteriöser Fall in Münster fordert das ungleiche Ermittlerduo heraus. Humor trifft Abgrund.", true)),
                Triple("21:45", "22:15", TvTemplate("Caren Miosga", "Politik-Talk live aus Berlin", TvCategory.NEWS, "Talkshow", "FSK 0", "Kritische Analysen und spannende Debatten mit führenden Politikern und Experten.")),
                Triple("22:15", "23:45", TvTemplate("Männer im Wahn", "Hochspannungs-Thriller", TvCategory.MOVIE, "Spielfilm", "FSK 16", "Ein ehemaliger Detektiv deckt eine Verschwörung in höchsten Regierungskreisen auf.")),
                Triple("23:45", "01:00", TvTemplate("Tagesthemen & Nachtmagazin", "Hintergründe & Analysen", TvCategory.NEWS, "Nachrichten", "FSK 0", "Detaillierte Berichte, Kommentare und Ausblick auf den nächsten Tag."))
            ),
            "zdf" to listOf(
                Triple("06:00", "09:00", TvTemplate("ZDF-Morgenmagazin", "Informationen am Morgen", TvCategory.NEWS, "Nachrichten", "FSK 0", "Sport, Wetter, Politik und Kultur im Stundentakt.")),
                Triple("09:00", "10:30", TvTemplate("Volle Kanne – Service täglich", "Wohnen, Gesundheit & Promis", TvCategory.ENTERTAINMENT, "Magazin", "FSK 0", "Nützliche Alltagstipps, leckere Rezepte und prominente Studiogäste.")),
                Triple("10:30", "12:00", TvTemplate("Notruf Hafenkante", "Folge 412 - Letzter Einsatz", TvCategory.SERIES, "Krimi", "FSK 12", "Polizei und Notärzte im Hamburger Hafen im unermüdlichen Schichtdienst.")),
                Triple("12:00", "13:00", TvTemplate("heute - Nachrichten", "Aktuelle Meldungen", TvCategory.NEWS, "Nachrichten", "FSK 0", "Die wichtigsten Themen des Tages auf den Punkt gebracht.")),
                Triple("13:00", "14:15", TvTemplate("Drehscheibe & Hallo Deutschland", "Länderberichte & Geschichten", TvCategory.NEWS, "Magazin", "FSK 0", "Menschen, Geschichten und Ereignisse quer durch die Bundesländer.")),
                Triple("14:15", "15:00", TvTemplate("Die Rosenheim-Cops", "Ein Fall für zwei Cops", TvCategory.SERIES, "Krimi", "FSK 6", "Mit bayerischem Charme löst das Ermittlerteam selbst verzwickte Mordfälle.")),
                Triple("15:00", "17:00", TvTemplate("Bares für Rares", "Die Trödel-Show mit Horst Lichter", TvCategory.ENTERTAINMENT, "Reality-Show", "FSK 0", "Experten bewerten Antiquitäten und Raritäten, danach geht es ab zu den Händlern!")),
                Triple("17:00", "18:00", TvTemplate("heute & hallo deutschland", "Nachrichten & Reportagen", TvCategory.NEWS, "Magazin", "FSK 0", "Aktuelles Zeitgeschehen und spannende Menschen im Porträt.")),
                Triple("18:00", "19:00", TvTemplate("SOKO Stuttgart", "Folge 310 - Der goldene Schuss", TvCategory.SERIES, "Krimi", "FSK 12", "Das SOKO-Team ermittelt in der baden-württembergischen Landeshauptstadt.")),
                Triple("19:00", "19:25", TvTemplate("heute Nachrichten", "Wetter & Sport", TvCategory.NEWS, "Nachrichten", "FSK 0", "Kompakte Berichterstattung über das Weltgeschehen.")),
                Triple("19:25", "20:15", TvTemplate("Notruf Hafenkante", "Elb-Spezial", TvCategory.SERIES, "Krimi", "FSK 12", "Sonderermittlung bei einem spektakulären Überfall im Hafen.")),
                Triple("20:15", "21:45", TvTemplate("Der Bergdoktor: Neue Horizonte", "Dramatische Rettung in den Alpen", TvCategory.SERIES, "Drama", "FSK 6", "Dr. Martin Gruber kämpft um das Leben einer jungen Alpinistin und sein eigenes Familienglück.", true)),
                Triple("21:45", "22:15", TvTemplate("heute journal", "Wetter & Analyse mit Marietta Slomka", TvCategory.NEWS, "Nachrichten", "FSK 0", "Detaillierte Hintergrundberichte, Interviews und Wirtschaftstrends.")),
                Triple("22:15", "23:15", TvTemplate("maybrit illner", "Der politische Talk im ZDF", TvCategory.NEWS, "Talkshow", "FSK 0", "Kontroverse Debatten zu den Brennpunkten unserer Zeit.")),
                Triple("23:15", "00:45", TvTemplate("ZDF-Filmpalast: Das Urteil", "Gerichts-Drama nach wahren Begebenheiten", TvCategory.MOVIE, "Drama", "FSK 12", "Ein packender Prozess erschüttert die Justiz und wirft moralische Fragen auf."))
            ),
            "pro7" to listOf(
                Triple("06:00", "09:30", TvTemplate("The Big Bang Theory", "Sitcom-Marathon (Staffel 8 & 9)", TvCategory.SERIES, "Comedy", "FSK 6", "Sheldon, Leonard, Penny und Howard erleben witzige Alltagsabenteuer zwischen Physik und Liebe.")),
                Triple("09:30", "12:00", TvTemplate("Two and a Half Men", "Die besten Folgen mit Charlie Sheen", TvCategory.SERIES, "Comedy", "FSK 12", "Jingles, Strandhaus und Frauen-Geschichten in Malibu.")),
                Triple("12:00", "14:00", TvTemplate("The Middle", "Familienwahnsinn im Mittleren Westen", TvCategory.SERIES, "Comedy", "FSK 6", "Frankie Heck versucht ihre dreiköpfige Chaotentruppe im Griff zu behalten.")),
                Triple("14:00", "16:00", TvTemplate("Young Sheldon", "Die Jugendjahre des Genies", TvCategory.SERIES, "Comedy", "FSK 6", "Der 9-jährige Sheldon Cooper geht bereits aufs Gymnasium in Texas.")),
                Triple("16:00", "17:00", TvTemplate("The Big Bang Theory", "Die Bazinga-Klassiker", TvCategory.SERIES, "Comedy", "FSK 6", "Wissenschaftlicher Humor und Nerd-Kultur pur.")),
                Triple("17:00", "18:00", TvTemplate("taff", "Das Lifestyle-Magazin mit Annemarie Carpendale", TvCategory.ENTERTAINMENT, "Magazin", "FSK 6", "Trends, Internet-Hypes, Promi-News und verblüffende Reportagen.")),
                Triple("18:00", "19:05", TvTemplate("Galileo", "Das ProSieben Wissensmagazin mit Aiman Abdallah", TvCategory.DOCUMENTARY, "Wissen", "FSK 0", "Faszination Wissen: Exklusive Tests, Food-Experimente und Technik der Zukunft.")),
                Triple("19:05", "20:15", TvTemplate("Galileo Stories", "Spektakuläre Entdeckungen weltweit", TvCategory.DOCUMENTARY, "Wissen", "FSK 6", "Atemberaubende Einblicke hinter die Kulissen von Mega-Projekten.")),
                Triple("20:15", "23:00", TvTemplate("Joko & Klaas gegen ProSieben", "Der ultimative Showdown um 15 Minuten Sendezeit", TvCategory.ENTERTAINMENT, "Show", "FSK 12", "Extrem-Herausforderungen, verrückte Spiele und maximale Unterhaltung im Duell.", true)),
                Triple("23:00", "00:30", TvTemplate("TV total", "Mit Sebastian Pufpaff", TvCategory.ENTERTAINMENT, "Comedy-Show", "FSK 12", "Der wöchentliche TV-Wahnsinn auf den Punkt sezierte TV-Ausschnitte und Stand-Up."))
            ),
            "rtl" to listOf(
                Triple("06:00", "08:30", TvTemplate("Punkt 6 / Punkt 7 / Punkt 8", "Frühstücksfernsehen live", TvCategory.NEWS, "Morgenmagazin", "FSK 0", "News, VIP-Geschichten, Verbrauchertipps und gute Laune am Morgen.")),
                Triple("08:30", "11:00", TvTemplate("Gute Zeiten, schlechte Zeiten", "Wiederholungen der Woche", TvCategory.SERIES, "Soap", "FSK 6", "Drama rund um den Kolle-Kiez in Berlin.")),
                Triple("11:00", "12:00", TvTemplate("Ulrich Wetzel – Das Strafgericht", "Juristische Fälle hautnah", TvCategory.ENTERTAINMENT, "Courtshow", "FSK 12", "Richter Ulrich Wetzel verhandelt emotionale und knifflige Fälle im Gerichtssaal.")),
                Triple("12:00", "13:00", TvTemplate("Punkt 12", "Das RTL-Mittagsmagazin", TvCategory.NEWS, "Magazin", "FSK 6", "Promis, Ratgeber, Schicksale und aktuelle Brennpunkte.")),
                Triple("13:00", "15:00", TvTemplate("Barbara Salesch – Das Strafgericht", "Gerechtigkeit im Fokus", TvCategory.ENTERTAINMENT, "Courtshow", "FSK 12", "Spannende Zeugenaussagen und schockierende Enthüllungen.")),
                Triple("15:00", "17:30", TvTemplate("Verkäufe, Deals & Herzblut", "Die Trödel-Profis", TvCategory.ENTERTAINMENT, "Reality", "FSK 0", "Experten helfen Familien beim Ausmisten und Schätzefinden auf dem Dachboden.")),
                Triple("17:30", "18:00", TvTemplate("Unter uns", "Folge 7250 - Entscheidung am Nachmittag", TvCategory.SERIES, "Soap", "FSK 6", "Geschichten aus der Schillerallee in Köln.")),
                Triple("18:00", "18:30", TvTemplate("Explosiv – Das Magazin", "Boulevard & Storys", TvCategory.NEWS, "Boulevard", "FSK 6", "Emotionale Geschichten, Enthüllungen und Prominente hautnah.")),
                Triple("18:30", "19:00", TvTemplate("Exclusiv – Das Starmagazin", "Mit Frauke Ludowig", TvCategory.NEWS, "Promis", "FSK 0", "Die neusten Gerüchte und Klatsch aus der Welt der Stars und Sternchen.")),
                Triple("19:00", "19:40", TvTemplate("RTL Aktuell", "News, Sport & Wetter mit Peter Kloeppel", TvCategory.NEWS, "Nachrichten", "FSK 0", "Die verlässliche Abend-Nachrichtensendung auf RTL.")),
                Triple("19:40", "20:15", TvTemplate("Gute Zeiten, schlechte Zeiten", "Folge 7980 - Das Versteck", TvCategory.SERIES, "Soap", "FSK 6", "Große Emotionen und dramatische Wendungen im Berliner Kiez.")),
                Triple("20:15", "23:15", TvTemplate("Wer wird Millionär?", "Das Prominenten-Spezial mit Günther Jauch", TvCategory.ENTERTAINMENT, "Quizshow", "FSK 0", "15 Fragen bis zur Million! Wer bewahrt kühlen Kopf auf dem heißen Stuhl?", true)),
                Triple("23:15", "00:00", TvTemplate("RTL Direkt & Extra", "Das Magazin mit Birgit Schrowange", TvCategory.NEWS, "Magazin", "FSK 12", "Investigative Reportagen, Tests und gesellschaftliche Brennpunkte."))
            ),
            "arte" to listOf(
                Triple("08:00", "12:00", TvTemplate("ARTE Entdeckung", "Die Wunder unseres Planeten", TvCategory.DOCUMENTARY, "Naturdoku", "FSK 0", "Faszination Natur: Atemberaubende HD-Aufnahmen aus den entlegensten Winkeln der Erde.")),
                Triple("12:00", "13:30", TvTemplate("Stadt Land Kunst", "Inspirationen aus Kultur & Reisen", TvCategory.DOCUMENTARY, "Kultur", "FSK 0", "Entdeckungsreise durch atemberaubende Landschaften, Architektur und Geschichte.")),
                Triple("13:30", "15:00", TvTemplate("ARTE Journal & Karambolage", "Europäische Perspektiven", TvCategory.NEWS, "Kulturmagazin", "FSK 0", "Analysen und kuriose Kulturunterschiede zwischen Deutschland und Frankreich.")),
                Triple("15:00", "18:00", TvTemplate("Geheimnisse des Universums", "Astronomie & Kosmologie", TvCategory.DOCUMENTARY, "Wissenschaft", "FSK 0", "Schwarze Löcher, Galaxien und die Entstehung des Lebens verständlich erklärt.")),
                Triple("18:00", "19:45", TvTemplate("Zu Tisch in...", "Kulinarische Schätze weltweit", TvCategory.DOCUMENTARY, "Reportage", "FSK 0", "Authentische Rezepte und ländliches Leben in europäischen Regionen.")),
                Triple("19:45", "20:15", TvTemplate("ARTE Journal", "Europäische Nachrichten", TvCategory.NEWS, "Nachrichten", "FSK 0", "Unabhängige europäische Nachrichten und Kulturberichte.")),
                Triple("20:15", "22:00", TvTemplate("Filmpalast: Das perfekte Geheimnis", "Preisgekröntes europäisches Cinema", TvCategory.MOVIE, "Drama", "FSK 12", "Ein Abendessen unter Freunden gerät außer Kontrolle, als alle Smartphones offen auf den Tisch gelegt werden.", true)),
                Triple("22:00", "23:30", TvTemplate("ARTE Concert: Live In Paris", "Symphonie & Moderne Sounds", TvCategory.ENTERTAINMENT, "Musik", "FSK 0", "Außergewöhnliche Live-Auftritte internationaler Künstler in HD-Klangqualität."))
            )
        )

        val channelNames = mapOf(
            "ard" to "Das Erste (ARD)", "zdf" to "ZDF", "rtl" to "RTL", "sat1" to "SAT.1",
            "pro7" to "ProSieben", "vox" to "VOX", "kabeleins" to "Kabel Eins", "arte" to "Arte",
            "3sat" to "3sat", "wdr" to "WDR Fernsehen", "eurosport" to "Eurosport 1", "kika" to "KiKa"
        )

        for ((chId, templates) in channelTemplates) {
            val chName = channelNames[chId] ?: chId.uppercase()
            for ((startStr, endStr, template) in templates) {
                val startTime = LocalDateTime.of(date, parseTime(startStr))
                var endTime = LocalDateTime.of(date, parseTime(endStr))
                if (endTime.isBefore(startTime)) {
                    endTime = endTime.plusDays(1)
                }

                resultList.add(
                    TvShow(
                        id = "${chId}_${date}_${startStr.replace(":", "")}",
                        title = template.title,
                        subtitle = template.subtitle,
                        channelId = chId,
                        channelName = chName,
                        startTime = startTime,
                        endTime = endTime,
                        description = template.description,
                        category = template.category,
                        genre = template.genre,
                        ageRating = template.ageRating,
                        year = "2024",
                        country = "Deutschland",
                        director = if (template.category == TvCategory.MOVIE) "Marcus H. Rosenmüller" else "Divers",
                        cast = listOf("Prominente Darsteller", "Gäste & Experten"),
                        isHighlight = template.isHighlight
                    )
                )
            }
        }

        // Fill remaining channels with generated schedules if not explicitly in map
        for (channel in defaultChannels) {
            if (!channelTemplates.containsKey(channel.id)) {
                var currentSlot = LocalDateTime.of(date, LocalTime.of(6, 0))
                val endOfDay = LocalDateTime.of(date.plusDays(1), LocalTime.of(2, 0))

                var index = 1
                while (currentSlot.isBefore(endOfDay)) {
                    val durationHours = if (index % 3 == 0) 2 else 1
                    val nextSlot = currentSlot.plusHours(durationHours.toLong())

                    val category = when (channel.id) {
                        "eurosport" -> TvCategory.SPORT
                        "kika" -> TvCategory.KIDS
                        "3sat", "wdr" -> if (index % 2 == 0) TvCategory.DOCUMENTARY else TvCategory.NEWS
                        else -> TvCategory.ENTERTAINMENT
                    }

                    val title = when (channel.id) {
                        "eurosport" -> listOf("Tennis: Grand Slam Live", "Radsport: Tour Highlight", "Motorsport Magazin", "Fußball Live-Analyse", "Snooker World Tour").random()
                        "kika" -> listOf("KiKa Live", "Schloss Einstein", "Logo! Die Kindernachrichten", "Die Sendung mit der Maus", "Checker Tobi").random()
                        "3sat" -> listOf("Kulturzeit", "NANO - Wissen aktuell", "Dokumentarfilm der Woche", "Alpenpanoramen", "Thementag Spezial").random()
                        "wdr" -> listOf("WDR aktuell", "Lokalzeit Aus Köln", "Wunderschön! Reisen im Westen", "Markt - Das Magazin", "Hier und heute").random()
                        else -> "${channel.name} Highlight #$index"
                    }

                    resultList.add(
                        TvShow(
                            id = "${channel.id}_${date}_$index",
                            title = title,
                            subtitle = "Ausgabe #$index - Sendeplatz $currentSlot",
                            channelId = channel.id,
                            channelName = channel.name,
                            startTime = currentSlot,
                            endTime = nextSlot,
                            description = "Spannendes Programm auf ${channel.name}. Abwechslungsreiche Unterhaltung, aktuelle Berichte und vieles mehr.",
                            category = category,
                            genre = category.displayName,
                            ageRating = "FSK 6",
                            isHighlight = index == 8
                        )
                    )
                    currentSlot = nextSlot
                    index++
                }
            }
        }

        return resultList.sortedBy { it.startTime }
    }

    private fun parseTime(timeStr: String): LocalTime {
        val parts = timeStr.split(":")
        return LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }

    private data class TvTemplate(
        val title: String,
        val subtitle: String,
        val category: TvCategory,
        val genre: String,
        val ageRating: String,
        val description: String,
        val isHighlight: Boolean = false
    )
}

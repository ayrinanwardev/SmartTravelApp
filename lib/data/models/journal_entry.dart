class JournalEntry {
  const JournalEntry({
    required this.id,
    required this.title,
    required this.body,
    required this.location,
    required this.date,
    required this.album,
    this.photoPaths = const [],
    this.coverUrl,
  });

  final String id;
  final String title;
  final String body;
  final String location;
  final DateTime date;
  final String album;

  /// Local file paths picked with image_picker.
  final List<String> photoPaths;

  /// Network cover for seeded sample entries.
  final String? coverUrl;

  Map<String, dynamic> toJson() => {
        'id': id,
        'title': title,
        'body': body,
        'location': location,
        'date': date.toIso8601String(),
        'album': album,
        'photoPaths': photoPaths,
        'coverUrl': coverUrl,
      };

  /// Parses the API's `JournalDto`. Photo URLs come back as "/uploads/x.jpg",
  /// so [resolveUrl] turns them into absolute URLs for the image widget.
  factory JournalEntry.fromApi(Map<String, dynamic> j, String Function(String) resolveUrl) => JournalEntry(
        id: j['id'] as String,
        title: j['title'] as String,
        body: (j['body'] as String?) ?? '',
        location: (j['location'] as String?) ?? '',
        date: DateTime.parse(j['date'] as String),
        album: (j['album'] as String?) ?? 'My Travels',
        photoPaths: ((j['photoUrls'] as List?) ?? const []).map((e) => resolveUrl(e as String)).toList(),
      );

  factory JournalEntry.fromJson(Map<String, dynamic> j) => JournalEntry(
        id: j['id'] as String,
        title: j['title'] as String,
        body: j['body'] as String,
        location: j['location'] as String,
        date: DateTime.parse(j['date'] as String),
        album: j['album'] as String,
        photoPaths: (j['photoPaths'] as List?)?.cast<String>() ?? const [],
        coverUrl: j['coverUrl'] as String?,
      );
}

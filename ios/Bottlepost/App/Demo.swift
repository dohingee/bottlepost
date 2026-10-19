import Foundation
import SwiftData

/**
 * Store screenshots (.github/workflows/ios-screenshots.yml). Debug builds started with
 * `-BPDemo YES -BPDemoScreen <inbox|compose|thread|waiting|connections>` show sample letters in the
 * phone's language, never talk to Firebase and ask for nothing. Release builds never enter it.
 */
enum Demo {
    enum Screen: String { case inbox, compose, thread, waiting, connections }

    #if DEBUG
    static let isOn = UserDefaults.standard.bool(forKey: "BPDemo")
    static var screen: Screen { Screen(rawValue: UserDefaults.standard.string(forKey: "BPDemoScreen") ?? "") ?? .inbox }
    #else
    static let isOn = false
    static var screen: Screen { .inbox }
    #endif

    static let threadId = "demo-seabreeze"
    static let waitingId = "demo-driftwood"

    /** Text already typed on the compose screenshot. */
    static var draft: String {
        #if DEBUG
        isOn && screen == .compose ? texts.draft : ""
        #else
        ""
        #endif
    }
}

#if DEBUG
extension Demo {
    struct Texts {
        /** Seabreeze, Lighthouse, Milky Way, Moonlight, Driftwood, Starfish. */
        let names: [String]
        let moonlight, lighthouse, milkyway: String
        let sea1, sea2, sea3: String
        let drift1, drift2: String
        let starfish: String
        let draft: String
    }

    static var texts: Texts { samples[deviceLang()] ?? samples["en"]! }

    @MainActor
    static func seed(_ context: ModelContext) {
        let t = texts
        let lang = deviceLang()
        let now = nowMs()
        let min: Int64 = 60_000
        var n = 0
        func thread(_ id: String, _ name: String, gender: String, mine: Bool, letters: [(Bool, String, Int64)],
                    unread: Int = 0, partnerLeft: Bool = false) {
            for (outgoing, body, at) in letters {
                n += 1
                context.insert(Letter(deliveryId: "demo\(n)", threadId: id, outgoing: outgoing, body: body,
                                      lang: lang, sentAt: at, sig: ""))
            }
            let last = letters.last!
            context.insert(LetterThread(threadId: id, startedByMe: mine, partnerNickname: name, partnerGender: gender,
                                        partnerLang: lang, createdAt: letters[0].2, lastAt: last.2,
                                        lastPreview: String(last.1.prefix(previewChars)),
                                        hasIncoming: letters.contains { !$0.0 }, hasOutgoing: letters.contains { $0.0 },
                                        unread: unread, closed: partnerLeft, partnerLeft: partnerLeft,
                                        lastOutgoing: last.0))
        }
        thread("demo-moonlight", t.names[3], gender: "F", mine: false, letters: [(false, t.moonlight, now - 10 * min)], unread: 1)
        thread("demo-lighthouse", t.names[1], gender: "M", mine: false, letters: [(false, t.lighthouse, now - 60 * min)])
        thread("demo-milkyway", t.names[2], gender: "F", mine: false, letters: [(false, t.milkyway, now - 120 * min)])
        thread(threadId, t.names[0], gender: "F", mine: false,
               letters: [(false, t.sea1, now - 26 * 60 * min), (true, t.sea2, now - 25 * 60 * min), (false, t.sea3, now - 25 * min)])
        thread(waitingId, t.names[4], gender: "M", mine: false,
               letters: [(false, t.drift1, now - 5 * 60 * min), (true, t.drift2, now - 40 * min)])
        thread("demo-starfish", t.names[5], gender: "F", mine: false,
               letters: [(false, t.starfish, now - 3 * 24 * 60 * min), (true, t.sea2, now - 2 * 24 * 60 * min)], partnerLeft: true)
        try? context.save()
    }

    static let samples: [String: Texts] = [
        "en": Texts(
            names: ["Seabreeze", "Lighthouse", "Milky Way", "Moonlight", "Driftwood", "Starfish"],
            moonlight: "The sunset on my way home was so beautiful that I wanted to tell someone. What did the sky look like where you are?",
            lighthouse: "Are you reading anything lately? I'm reading a book of poems about the sea, one poem each night.",
            milkyway: "This is my first letter, so I'm a little nervous. What's your favorite season, and why?",
            sea1: "Hello, stranger. I was walking along the beach today and suddenly felt like writing to someone.",
            sea2: "Nice to meet you! My day was long too, but your letter cheered me up. How have you been?",
            sea3: "I've started taking a walk every morning. One small habit really changes the whole day.",
            drift1: "Hello from a rainy evening. I made soup for the first time and it actually turned out well!",
            drift2: "Congratulations on the soup! I just moved to a new city. Tell me something good from your week?",
            starfish: "Thank you for the kind letters. I'm going on a long trip, so this is goodbye. Take care!",
            draft: "Hello, whoever finds this bottle. It rained all day here, so I stayed in with warm tea and an old song. What does your day look like?"),
        "ko": Texts(
            names: ["바닷바람", "등대지기", "은하수", "달빛", "유목", "불가사리"],
            moonlight: "퇴근길 노을이 너무 예뻐서 누군가에게 꼭 말하고 싶었어요. 당신이 있는 곳의 하늘은 어땠나요?",
            lighthouse: "요즘 읽는 책 있어요? 저는 바다에 관한 시집을 하루에 한 편씩 읽고 있어요.",
            milkyway: "첫 편지라 조금 떨리네요. 가장 좋아하는 계절은 언제예요? 이유도 궁금해요.",
            sea1: "안녕하세요, 낯선 분. 오늘 바닷가를 걷다가 문득 누군가에게 편지를 쓰고 싶어졌어요.",
            sea2: "반가워요! 저도 긴 하루였는데 편지 덕분에 기분이 좋아졌어요. 요즘 어떻게 지내요?",
            sea3: "요즘 아침마다 산책을 시작했어요. 작은 습관 하나가 하루를 정말 바꿔 놓네요.",
            drift1: "비 오는 저녁에 인사드려요. 처음으로 수프를 끓여 봤는데 꽤 맛있게 됐어요!",
            drift2: "수프 성공 축하해요! 저는 얼마 전 새 도시로 이사 왔어요. 이번 주에 좋았던 일 하나 들려줄래요?",
            starfish: "다정한 편지 고마웠어요. 긴 여행을 떠나게 돼서 여기서 인사할게요. 잘 지내요!",
            draft: "이 병을 발견한 누군가에게. 오늘은 하루 종일 비가 와서 따뜻한 차와 오래된 노래로 하루를 보냈어요. 당신의 하루는 어땠나요?"),
        "ja": Texts(
            names: ["潮風", "灯台守", "天の川", "月明かり", "流木", "ヒトデ"],
            moonlight: "帰り道の夕焼けがとてもきれいで、誰かに伝えたくなりました。あなたのいる場所の空はどうでしたか？",
            lighthouse: "最近何か読んでいますか？私は海の詩集を、毎晩一編ずつ読んでいます。",
            milkyway: "初めての手紙なので少し緊張しています。いちばん好きな季節はいつですか？理由も知りたいです。",
            sea1: "こんにちは、見知らぬ方。今日、海辺を歩いていたら、ふと誰かに手紙を書きたくなりました。",
            sea2: "はじめまして！私も長い一日でしたが、お手紙で元気が出ました。最近どうしていますか？",
            sea3: "最近、毎朝散歩を始めました。小さな習慣ひとつで、一日が本当に変わりますね。",
            drift1: "雨の夜にこんばんは。初めてスープを作ってみたら、なかなかおいしくできました！",
            drift2: "スープ成功おめでとう！私は最近新しい街に引っ越してきました。今週あった良いことを教えてくれますか？",
            starfish: "優しいお手紙をありがとう。長い旅に出るので、ここでお別れです。お元気で！",
            draft: "このボトルを見つけた誰かへ。今日は一日中雨だったので、温かいお茶と懐かしい歌で過ごしました。あなたの一日はどうでしたか？"),
        "es": Texts(
            names: ["Brisa", "Farero", "Vía Láctea", "Luz de luna", "Madera", "Estrella"],
            moonlight: "El atardecer de camino a casa era tan bonito que quise contárselo a alguien. ¿Cómo estaba el cielo donde estás tú?",
            lighthouse: "¿Estás leyendo algo últimamente? Yo leo un libro de poemas sobre el mar, un poema cada noche.",
            milkyway: "Es mi primera carta, así que estoy un poco nerviosa. ¿Cuál es tu estación favorita y por qué?",
            sea1: "Hola, desconocido. Hoy caminaba por la playa y de repente me dieron ganas de escribirle a alguien.",
            sea2: "¡Encantado! Mi día también fue largo, pero tu carta me alegró. ¿Cómo has estado?",
            sea3: "Empecé a salir a caminar cada mañana. Un pequeño hábito de verdad cambia todo el día.",
            drift1: "Hola desde una tarde lluviosa. Hice sopa por primera vez ¡y me salió bastante bien!",
            drift2: "¡Felicidades por la sopa! Acabo de mudarme a una ciudad nueva. ¿Me cuentas algo bueno de tu semana?",
            starfish: "Gracias por tus cartas tan amables. Me voy de viaje largo, así que me despido aquí. ¡Cuídate!",
            draft: "Hola a quien encuentre esta botella. Aquí llovió todo el día, así que me quedé con un té caliente y una canción antigua. ¿Cómo es tu día?"),
        "pt": Texts(
            names: ["Brisa", "Faroleiro", "Via Láctea", "Luar", "Madeira", "Estrela"],
            moonlight: "O pôr do sol no caminho de casa estava tão lindo que eu quis contar para alguém. Como estava o céu aí onde você está?",
            lighthouse: "Você está lendo alguma coisa? Eu estou lendo um livro de poemas sobre o mar, um poema por noite.",
            milkyway: "É a minha primeira carta, então estou um pouco nervosa. Qual é a sua estação favorita, e por quê?",
            sea1: "Olá, desconhecido. Hoje eu estava caminhando pela praia e de repente deu vontade de escrever para alguém.",
            sea2: "Prazer! Meu dia também foi longo, mas sua carta me animou. Como você tem passado?",
            sea3: "Comecei a caminhar toda manhã. Um pequeno hábito muda mesmo o dia inteiro.",
            drift1: "Olá de uma noite chuvosa. Fiz sopa pela primeira vez e ficou bem gostosa!",
            drift2: "Parabéns pela sopa! Acabei de me mudar para uma cidade nova. Me conta algo bom da sua semana?",
            starfish: "Obrigada pelas cartas tão gentis. Vou fazer uma longa viagem, então me despeço aqui. Se cuida!",
            draft: "Olá, quem encontrar esta garrafa. Choveu o dia todo aqui, então fiquei em casa com um chá quente e uma música antiga. Como está o seu dia?"),
        "id": Texts(
            names: ["Angin Laut", "Mercusuar", "Bima Sakti", "Cahaya Bulan", "Kayu Hanyut", "Bintang Laut"],
            moonlight: "Matahari terbenam dalam perjalanan pulang tadi indah sekali, sampai saya ingin menceritakannya. Bagaimana langit di tempat Anda?",
            lighthouse: "Apakah Anda sedang membaca sesuatu? Saya sedang membaca buku puisi tentang laut, satu puisi setiap malam.",
            milkyway: "Ini surat pertama saya, jadi saya agak gugup. Musim apa yang paling Anda sukai, dan mengapa?",
            sea1: "Halo, orang asing. Hari ini saya berjalan di tepi pantai dan tiba-tiba ingin menulis surat untuk seseorang.",
            sea2: "Senang berkenalan! Hari saya juga panjang, tetapi surat Anda membuat saya senang. Apa kabar Anda?",
            sea3: "Saya mulai berjalan-jalan setiap pagi. Satu kebiasaan kecil benar-benar mengubah seluruh hari.",
            drift1: "Halo dari sore yang hujan. Saya memasak sup untuk pertama kalinya dan ternyata enak!",
            drift2: "Selamat atas supnya! Saya baru pindah ke kota baru. Maukah Anda menceritakan satu hal baik minggu ini?",
            starfish: "Terima kasih atas surat-surat yang hangat. Saya akan pergi jauh, jadi saya pamit di sini. Jaga diri!",
            draft: "Halo, siapa pun yang menemukan botol ini. Di sini hujan seharian, jadi saya di rumah dengan teh hangat dan lagu lama. Bagaimana hari Anda?"),
        "vi": Texts(
            names: ["Gió biển", "Người gác hải đăng", "Ngân Hà", "Ánh trăng", "Gỗ trôi", "Sao biển"],
            moonlight: "Hoàng hôn trên đường về nhà đẹp đến mức mình muốn kể cho ai đó nghe. Bầu trời chỗ bạn hôm nay thế nào?",
            lighthouse: "Dạo này bạn có đọc gì không? Mình đang đọc một tập thơ về biển, mỗi tối một bài.",
            milkyway: "Đây là lá thư đầu tiên nên mình hơi hồi hộp. Bạn thích mùa nào nhất, và vì sao?",
            sea1: "Chào người lạ. Hôm nay mình đi dạo dọc bờ biển và bỗng muốn viết thư cho ai đó.",
            sea2: "Rất vui được làm quen! Ngày của mình cũng dài, nhưng thư của bạn làm mình vui hẳn. Dạo này bạn thế nào?",
            sea3: "Mình bắt đầu đi bộ mỗi sáng. Một thói quen nhỏ thật sự thay đổi cả ngày.",
            drift1: "Chào bạn từ một buổi tối mưa. Lần đầu mình nấu súp mà lại ngon ra phết!",
            drift2: "Chúc mừng món súp nhé! Mình vừa chuyển đến một thành phố mới. Kể mình nghe một điều vui trong tuần của bạn nhé?",
            starfish: "Cảm ơn những lá thư ấm áp. Mình sắp đi xa nên xin chào tạm biệt ở đây. Giữ gìn sức khỏe nhé!",
            draft: "Gửi người nhặt được chai này. Hôm nay ở đây mưa cả ngày, mình ở nhà với tách trà nóng và một bài hát cũ. Ngày của bạn thế nào?"),
        "th": Texts(
            names: ["ลมทะเล", "ผู้เฝ้าประภาคาร", "ทางช้างเผือก", "แสงจันทร์", "ขอนไม้ลอย", "ปลาดาว"],
            moonlight: "พระอาทิตย์ตกระหว่างทางกลับบ้านสวยมากจนอยากเล่าให้ใครสักคนฟัง ท้องฟ้าที่คุณอยู่เป็นอย่างไรบ้าง",
            lighthouse: "ช่วงนี้อ่านหนังสืออะไรอยู่ไหม ฉันกำลังอ่านหนังสือกลอนเกี่ยวกับทะเล คืนละหนึ่งบท",
            milkyway: "นี่เป็นจดหมายฉบับแรก เลยตื่นเต้นนิดหน่อย คุณชอบฤดูไหนที่สุด และเพราะอะไร",
            sea1: "สวัสดี คนแปลกหน้า วันนี้เดินเล่นริมชายหาดแล้วจู่ ๆ ก็อยากเขียนจดหมายถึงใครสักคน",
            sea2: "ยินดีที่ได้รู้จัก วันนี้ของฉันก็ยาวนาน แต่จดหมายของคุณทำให้อารมณ์ดีขึ้น ช่วงนี้เป็นอย่างไรบ้าง",
            sea3: "ช่วงนี้เริ่มเดินเล่นทุกเช้า นิสัยเล็ก ๆ อย่างเดียวเปลี่ยนทั้งวันได้จริง ๆ",
            drift1: "สวัสดีจากเย็นวันฝนตก เพิ่งลองทำซุปครั้งแรก แล้วออกมาอร่อยด้วย",
            drift2: "ยินดีด้วยกับซุปนะ ฉันเพิ่งย้ายมาเมืองใหม่ เล่าเรื่องดี ๆ ในสัปดาห์นี้ให้ฟังหน่อยได้ไหม",
            starfish: "ขอบคุณสำหรับจดหมายแสนอบอุ่น ฉันกำลังจะเดินทางไกล เลยขอลาตรงนี้ ดูแลตัวเองด้วยนะ",
            draft: "ถึงใครก็ตามที่พบขวดนี้ วันนี้ฝนตกทั้งวัน เลยอยู่บ้านกับชาอุ่น ๆ และเพลงเก่า ๆ วันนี้ของคุณเป็นอย่างไรบ้าง"),
    ]
}

/** Offline stand-in for the callables while taking screenshots. */
final class DemoService: BottlepostService, @unchecked Sendable {
    var uid: String? { "demo" }
    func signInIfNeeded() async throws {}
    func getProfile() async throws -> Profile? {
        Profile(callable: ["gender": "F", "birthYear": 1995, "lang": deviceLang(), "nickname": Demo.texts.names[0]])
    }
    func createProfile(_ p: NewProfile) async throws -> Profile { try await getProfile()! }
    func updateProfile(_ u: ProfileUpdate) async throws -> Profile { try await getProfile()! }
    func registerPushToken(_ token: String) async throws {}
    func sendLetter(body: String, wantGender: String, sameLanguageOnly: Bool) async throws -> SentLetter { throw ApiError(code: "unknown") }
    func replyLetter(threadId: String, body: String) async throws -> SentLetter { throw ApiError(code: "unknown") }
    func leaveThread(_ threadId: String) async throws {}
    func blockThread(_ threadId: String) async throws {}
    func submitReport(threadId: String, reason: String, note: String, messages: [ReportMessage]) async throws {}
    func submitAppeal(_ message: String) async throws {}
    func deleteAccount() async throws {}
    func signOut() {}
    var isAppleLinked: Bool { false }
    func linkApple(_ credential: AppleCredential) async throws -> LinkOutcome { .linked }
    func switchToLinkedApple() async throws {}
    func deleteCurrentUser() async throws {}
    func confirmAccountLink() async throws -> Profile { try await getProfile()! }
    func getBackupKey() async throws -> Data { Data(count: 32) }
    func revokeApple(authorizationCode: String) async throws {}
}
#endif

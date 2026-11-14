# Pixel Snap

แอปกล้องบน Android ที่เปลี่ยนภาพจากกล้องให้เป็น **พิกเซลอาร์ตสไตล์การ์ตูน** แบบสด ๆ ตั้งแต่ก่อนกดถ่าย
พร้อมอัลบั้มเก็บความทรงจำ หน้าตาแอปออกแบบเป็นธีมเรโทร หน้าต่างแบบ Windows 95 บนพื้นตารางสีฟ้า

> Term project วิชา **CS364** (ภาคเรียนที่ 1)

| ถ่ายรูป (`camera.exe`) | อัลบั้ม (`memories.folder`) | ดูรูปใหญ่ |
|:---:|:---:|:---:|
| <img src="docs/screenshots/camera.png" width="240"> | <img src="docs/screenshots/album.png" width="240"> | <img src="docs/screenshots/viewer.png" width="240"> |

## ฟีเจอร์

**หน้าถ่ายรูป**
- ภาพสดจากกล้องถูกแปลงเป็นพิกเซลการ์ตูนทันที (ป้าย `PIXEL LIVE`)
- เลือกสไตล์ได้ 3 ระดับ
  - **8 BIT:** บล็อกใหญ่ 15 สี
  - **16 BIT:** บล็อกกลาง 23 สี
  - **32 BIT:** บล็อกเล็ก 33 สี
- ซูม 1× / 2× / 3× (ซูมจริงของกล้อง)
- สลับกล้องหน้า/หลัง กล้องหน้าแสดงภาพกลับซ้ายขวาเหมือนส่องกระจก
- กดถ่ายแล้วมีแฟลช ภาพค้างให้เห็นครู่หนึ่ง และบันทึกลงอัลบั้มทันที
- ตัวนับจำนวนรูป (สูงสุด 99 รูป) และปุ่มรูปล่าสุด กดแล้วไปหน้าอัลบั้ม

**หน้าอัลบั้ม**
- แท็บ **All** / **Liked** พร้อมจำนวนรูป
- สลับมุมมองได้ระหว่างกริด 2 คอลัมน์และรายการ
- แตะรูป = กดถูกใจหรือยกเลิก
- กดค้างที่รูป = เปิดดูรูปใหญ่ ซึ่งกดถูกใจหรือลบรูปได้ (มีหน้าต่างยืนยันก่อนลบ)
- มีภาพตัวอย่าง 6 รูปจากงานออกแบบติดมาให้ตั้งแต่ติดตั้ง

## เทคโนโลยีที่ใช้

| ส่วน | ที่ใช้ |
|---|---|
| ภาษา / UI | Java, XML layouts, Fragments, RecyclerView |
| กล้อง | CameraX 1.4.2 (`ImageAnalysis`) |
| ฐานข้อมูล | SQLite ผ่าน `SQLiteOpenHelper` (`DBHelper`) |
| Android | minSdk 28 (Android 9), targetSdk / compileSdk 37 |
| Build | Gradle (Android Gradle Plugin 9.3.3) |

ทุกอย่างทำงานในเครื่องทั้งหมด **ไม่ใช้อินเทอร์เน็ตและไม่เรียก API ภายนอก**

## หลักการทำฟิลเตอร์พิกเซลการ์ตูน

CameraX ส่งภาพจากกล้อง (ประมาณ 640×480) มาให้ราว 30 เฟรมต่อวินาที แต่ละเฟรมผ่าน `Pixelator` ตามขั้นตอนนี้

```
เฟรมกล้อง
  → ย่อเหลือ 2 เท่าของขนาดสุดท้าย
  → ① เกลี่ยเรียบด้วย Kuwahara filter (ผิว/พื้นผิวเรียบเป็นก้อนสี แต่ขอบยังคม)
  → ย่อเป็นขนาดสุดท้าย กว้าง 40 / 64 / 96 พิกเซล
  → ② เพิ่มความสดของสี คอนทราสต์ และความสว่าง
  → ③ ปัดทุกพิกเซลให้เข้าจานสีที่กำหนด (มีสีผิวหลายระดับ)
  → ④ ลบจุดสีเดี่ยว ๆ ให้เป็นก้อนสีสะอาด
  → ⑤ ใส่เส้นขอบสีน้ำตาลเข้มตรงขอบวัตถุ
  → หมุนให้ตรง / กลับซ้ายขวาสำหรับกล้องหน้า
```

ภาพที่ได้มีขนาดเล็กมาก เวลาแสดงจะขยายแบบไม่เกลี่ยสี (nearest-neighbor) ทำให้เห็นเป็นบล็อกพิกเซลคม ๆ
รูปที่บันทึกคือเฟรมเดียวกับที่เห็นในช่องมองภาพตอนกดถ่ายพอดี

## ฐานข้อมูล

ไฟล์ `pixel_snap.db` (เวอร์ชัน 3) มีตารางเดียว

```sql
CREATE TABLE memories (
    _id          INTEGER PRIMARY KEY AUTOINCREMENT,
    taken_at     INTEGER NOT NULL,            -- เวลาถ่าย (Unix time, มิลลิวินาที)
    is_favorite  INTEGER NOT NULL DEFAULT 0,  -- 1 = ถูกใจ
    image_base64 TEXT    NOT NULL             -- ไฟล์รูป (PNG/JPEG) แปลงเป็น Base64
);
CREATE INDEX idx_memories_taken_at ON memories (taken_at DESC);
```

- **การเก็บรูป:** แปลงรูปเป็นข้อความ ASCII ด้วย Base64 ก่อนบันทึก และแปลงกลับตอนแสดงผล ทุกภาพในแอปดึงจากฐานข้อมูล
- **CRUD อยู่ใน `MemoryRepository`:**
  - INSERT: ถ่ายรูป
  - SELECT: แสดงอัลบั้มและรูป
  - UPDATE: กดถูกใจ
  - DELETE: ลบรูป
- **การอัปเกรด:** `DBHelper.onUpgrade()` ย้ายข้อมูลจากฐานข้อมูลเวอร์ชันเก่าให้อัตโนมัติ รูปของผู้ใช้ไม่หาย

## โครงสร้างโปรเจกต์

```
app/src/main/java/com/example/myapplication/
├── MainActivity.java           หน้าหลัก + แถบเมนูล่าง สลับระหว่าง 2 หน้า
├── camera/
│   └── CameraFragment.java     หน้าถ่ายรูป: CameraX, สิทธิ์กล้อง, ซูม, ถ่ายรูป
├── gallery/
│   ├── GalleryFragment.java    หน้าอัลบั้ม: แท็บ, กริด/รายการ, ถูกใจ
│   ├── MemoryAdapter.java      การ์ดรูปใน RecyclerView
│   └── PhotoViewerDialog.java  หน้าต่างดูรูปใหญ่ (ถูกใจ / ลบ)
├── data/
│   ├── DBHelper.java           SQLiteOpenHelper: สร้างตาราง, ข้อมูลตั้งต้น, อัปเกรด, Base64
│   ├── MemoryRepository.java   อ่าน/เขียนฐานข้อมูล (CRUD) + แคชรูป
│   └── Memory.java             ข้อมูลรูป 1 แถว
└── ui/
    ├── Pixelator.java          ฟิลเตอร์พิกเซลการ์ตูน
    ├── PhotoViews.java         แสดงรูปพิกเซลแบบขอบคม / ภาพว่าง
    ├── PixelFont.java          ฟอนต์พิกเซล
    └── GridBackgroundDrawable.java  พื้นหลังลายตาราง

app/src/main/res/
├── layout/   activity_main, fragment_camera, fragment_gallery, item_memory, dialog_photo_viewer
├── drawable/ กรอบหน้าต่าง ปุ่ม และไอคอนแบบเรโทร (vector / shape)
├── font/     Silkscreen (หัวข้อ), Pixelify Sans (ข้อความ)
└── raw/      ภาพตัวอย่าง 6 รูป (ใส่ลงฐานข้อมูลตอนติดตั้ง)
```

## วิธีรันโปรเจกต์

1. ติดตั้ง **Android Studio** เวอร์ชันล่าสุด (มี JDK มาให้แล้ว)
2. Clone โปรเจกต์
   ```bash
   git clone https://github.com/Nantapoparm/CS364_TermProject.git
   ```
3. เปิดโฟลเดอร์ใน Android Studio แล้วรอ **Gradle Sync** ให้เสร็จ
4. เลือกอุปกรณ์ แล้วกด **Run ▶**
   - **มือถือจริง:** Android 9 ขึ้นไป แนะนำ เพราะได้ทดสอบกล้องจริง
   - **Emulator:** ใช้กล้องจำลองได้

ครั้งแรกที่เปิดหน้ากล้อง แอปจะขอสิทธิ์ใช้กล้อง

## สิทธิ์ที่แอปใช้

| สิทธิ์ | ใช้ทำอะไร |
|---|---|
| `CAMERA` | แสดงภาพสดและถ่ายรูป |

รูปทั้งหมดเก็บในฐานข้อมูลส่วนตัวของแอปเท่านั้น ไม่ส่งออกนอกเครื่อง และแอปอื่นเข้าถึงไม่ได้

## เครดิต

- ฟอนต์ [Silkscreen](https://github.com/googlefonts/silkscreen) และ [Pixelify Sans](https://github.com/eifetx/Pixelify-Sans) ใช้ภายใต้ SIL Open Font License 1.1 (ดูในโฟลเดอร์ [`licenses/`](licenses/))
  - Pixelify Sans ถูกแยกเป็นไฟล์ Regular / Bold และตัดตัวเลข 0–9 ออก เพื่อให้ตัวเลขแสดงด้วยฟอนต์ปกติของระบบ
- ภาพตัวอย่าง 6 รูปมาจากงานออกแบบ UI ของโปรเจกต์

## ผู้จัดทำ

- _(เพิ่มชื่อ–รหัสนักศึกษา)_

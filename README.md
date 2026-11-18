# SMS Reader (Android, Kotlin)

আপনার APK-র (com.ishan.sms) মতো UI ও কাজ + নতুন ফিচার: **অ্যাপ থেকে ম্যানুয়ালি TrxID যোগ**
(SMS পেজের নিচে ডানে "TrxID যোগ করুন" বাটন)।

## বিল্ড
1. Android Studio (Koala+) → Open → এই ফোল্ডার। Gradle sync শেষ হলে Run।
2. APK: Build > Build Bundle(s)/APK(s) > Build APK(s)।

## ব্যবহার
1. admin.html → SMS Reader → Generate Key।
2. অ্যাপে "প্রজেক্ট যোগ করুন" → নাম, Database URL, Key দিন → সংরক্ষণ।
3. সেটিংসে "সব পারমিশন একসাথে দিন"।
4. অ্যাপ bKash/Nagad/Rocket-এর "received" SMS ধরে `sms_inbox/{TrxID}`-এ আপলোড করবে।

## Firebase rules
`firebase-rules.json` শুধু `readers` ও `sms_inbox` অংশ; আপনার বাকি রুলসের সাথে মার্জ করুন
এবং ADMIN_EMAIL_HERE বদলান।

## নোট
- Google Play SMS permission-এ কড়াকড়ি করে; এটি সরাসরি APK ইনস্টলের জন্য।
- মূল ফাইল: SmsReceiver.kt (ধরা), SmsParser.kt (পার্স), Api.kt/Sync.kt (Firebase), MainActivity.kt (UI)।

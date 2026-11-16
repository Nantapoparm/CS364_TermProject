package com.example.myapplication.data;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;

/**
 * [ใหม่ - ฟีเจอร์อวาตาร์] ข้อมูล 1 แถวของตาราง {@code avatars} (คู่กับ {@link Memory} ของตาราง memories)
 *
 * <p>เก็บแค่ส่วนที่ตัดออกมาจากรูป (PNG โปร่งใส) ส่วนขาและเฟรมเดิน/กระโดดสร้างใหม่จากโค้ดใน
 * {@code AvatarSprite} ทุกครั้งที่โหลด ทำให้ข้อมูลในฐานข้อมูลเล็ก
 */
public final class Avatar {
    private final long id;
    private final long createdAt;
    private final Bitmap cutOut;

    Avatar(long id, long createdAt, @NonNull Bitmap cutOut) {
        this.id = id;
        this.createdAt = createdAt;
        this.cutOut = cutOut;
    }

    public long getId() {
        return id;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    /** Small transparent PNG of the cut-out part, without outline. */
    @NonNull
    public Bitmap getCutOut() {
        return cutOut;
    }
}

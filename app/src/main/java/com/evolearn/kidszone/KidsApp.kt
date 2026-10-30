package com.evolearn.kidszone

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.evolearn.kidszone.core.BiliApi
import com.evolearn.kidszone.core.Prefs
import okhttp3.Interceptor

class KidsApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }

    /** 封面图加载器：统一带浏览器UA+B站Referer，防封面CDN防盗链 */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(
                okhttp3.OkHttpClient.Builder()
                    .addInterceptor { chain: Interceptor.Chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", BiliApi.UA)
                                .header("Referer", "https://www.bilibili.com/")
                                .build()
                        )
                    }
                    .build()
            )
            .crossfade(true)
            .build()
}

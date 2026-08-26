package cn.weiekko.dock.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import cn.weiekko.dock.MainActivity
import cn.weiekko.dock.R
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import kotlin.concurrent.thread

class WakeWordService : Service() {
    @Volatile
    private var running = false
    private var recordThread: Thread? = null
    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startInForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!running) startLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        recordThread?.join(400)
        releaseMic()
        stream?.release()
        stream = null
        kws?.release()
        kws = null
        super.onDestroy()
    }

    private fun startLoop() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        running = true
        recordThread = thread(name = "wake-kws", isDaemon = true) {
            try {
                listen()
            } catch (e: Throwable) {
                Log.e(TAG, "wake word loop failed", e)
            } finally {
                running = false
                releaseMic()
                stream?.release()
                stream = null
                kws?.release()
                kws = null
            }
        }
    }

    private fun listen() {
        val spotter = KeywordSpotter(
            assetManager = assets,
            config = KeywordSpotterConfig(
                featConfig = getFeatureConfig(SAMPLE_RATE, 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = WakeKeywords.ENCODER,
                        decoder = WakeKeywords.DECODER,
                        joiner = WakeKeywords.JOINER,
                    ),
                    tokens = WakeKeywords.TOKENS,
                    numThreads = 1,
                    debug = false,
                    provider = "cpu",
                    modelType = "zipformer2",
                ),
                keywordsFile = WakeKeywords.KEYWORDS_FILE,
                keywordsScore = 1.8f,
                keywordsThreshold = 0.25f,
                numTrailingBlanks = 2,
            ),
        )
        kws = spotter
        val nextStream = spotter.createStream(WakeKeywords.STREAM)
        stream = nextStream

        val minBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) {
            Log.e(TAG, "AudioRecord buffer size invalid: $minBytes")
            stopSelf()
            return
        }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBytes * 2,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e(TAG, "AudioRecord failed to initialize")
            stopSelf()
            return
        }
        audioRecord = recorder
        recorder.startRecording()

        val chunk = (SAMPLE_RATE * 0.1).toInt()
        val buffer = ShortArray(chunk)
        var lastDetectAt = 0L

        while (running) {
            val n = recorder.read(buffer, 0, buffer.size)
            if (n <= 0) continue
            val samples = FloatArray(n) { buffer[it] / 32768.0f }
            nextStream.acceptWaveform(samples, SAMPLE_RATE)
            while (spotter.isReady(nextStream)) {
                spotter.decode(nextStream)
                val keyword = spotter.getResult(nextStream).keyword
                if (keyword.isBlank()) continue
                val now = SystemClock.elapsedRealtime()
                if (now - lastDetectAt < COOLDOWN_MS) {
                    spotter.reset(nextStream)
                    continue
                }
                lastDetectAt = now
                spotter.reset(nextStream)
                onDetected(keyword)
            }
        }
    }

    private fun onDetected(keyword: String) {
        Log.i(TAG, "wake word: $keyword")
        WakeChime.play()
        startActivity(MainActivity.wakeWordIntent(this, keyword))
    }

    private fun releaseMic() {
        val recorder = audioRecord
        audioRecord = null
        if (recorder == null) return
        runCatching { recorder.stop() }
        recorder.release()
    }

    private fun startInForeground() {
        val pending = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_helm)
            .setContentTitle(getString(R.string.wake_word_title))
            .setContentText(getString(R.string.wake_word_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_word_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                description = getString(R.string.wake_word_text)
            },
        )
    }

    companion object {
        private const val TAG = "WakeWord"
        private const val CHANNEL_ID = "wake_word"
        private const val NOTIFICATION_ID = 48
        private const val SAMPLE_RATE = 16_000
        private const val COOLDOWN_MS = 1_200L

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}

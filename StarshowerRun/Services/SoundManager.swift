//
//  SoundManager.swift
//  StarshowerRun
//
//  Plays the synthesised effects and music loop through AVAudioEngine.
//

import AVFoundation

@MainActor
final class SoundManager {
    static let shared = SoundManager()

    private let engine = AVAudioEngine()
    private let format: AVAudioFormat
    private var effectPlayers: [AVAudioPlayerNode] = []
    private let musicPlayer = AVAudioPlayerNode()
    private var buffers: [SoundEffect: AVAudioPCMBuffer] = [:]
    private var musicBuffer: AVAudioPCMBuffer?
    private var nextPlayer = 0
    private var isSetUp = false
    private var isMusicScheduled = false
    private var lastPlayed: [SoundEffect: TimeInterval] = [:]

    private init() {
        format = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1)!
    }

    var effectsEnabled: Bool {
        UserDefaults.standard.object(forKey: SettingsKey.soundEnabled) as? Bool ?? true
    }

    var musicEnabled: Bool {
        UserDefaults.standard.object(forKey: SettingsKey.musicEnabled) as? Bool ?? true
    }

    // MARK: Setup

    func prepare() {
        guard !isSetUp else { return }
        isSetUp = true

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.ambient, mode: .default, options: [.mixWithOthers])
        try? session.setActive(true)

        for _ in 0..<10 {
            let player = AVAudioPlayerNode()
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: format)
            effectPlayers.append(player)
        }
        engine.attach(musicPlayer)
        engine.connect(musicPlayer, to: engine.mainMixerNode, format: format)
        musicPlayer.volume = 0.32
        engine.mainMixerNode.outputVolume = 0.85

        for effect in SoundEffect.allCases {
            buffers[effect] = makeBuffer(SynthSamples.samples(for: effect, sampleRate: format.sampleRate))
        }

        startEngine()

        NotificationCenter.default.addObserver(forName: .AVAudioEngineConfigurationChange,
                                               object: engine, queue: .main) { [weak self] _ in
            Task { @MainActor in
                self?.isMusicScheduled = false
                self?.startEngine()
            }
        }
    }

    private func startEngine() {
        guard !engine.isRunning else { return }
        engine.prepare()
        try? engine.start()
    }

    private func makeBuffer(_ samples: [Float]) -> AVAudioPCMBuffer? {
        guard !samples.isEmpty,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: AVAudioFrameCount(samples.count)),
              let channel = buffer.floatChannelData?[0] else { return nil }
        buffer.frameLength = AVAudioFrameCount(samples.count)
        samples.withUnsafeBufferPointer { source in
            if let base = source.baseAddress {
                channel.update(from: base, count: samples.count)
            }
        }
        return buffer
    }

    // MARK: Effects

    func play(_ effect: SoundEffect) {
        guard effectsEnabled, isSetUp, let buffer = buffers[effect] else { return }

        // Avoid machine-gunning the same sound within a couple of frames.
        let now = ProcessInfo.processInfo.systemUptime
        if let last = lastPlayed[effect], now - last < 0.035 { return }
        lastPlayed[effect] = now

        if !engine.isRunning { startEngine() }
        guard engine.isRunning else { return }

        let player = effectPlayers[nextPlayer]
        nextPlayer = (nextPlayer + 1) % effectPlayers.count
        player.scheduleBuffer(buffer, at: nil, options: .interrupts, completionHandler: nil)
        if !player.isPlaying { player.play() }
    }

    // MARK: Music

    func startMusic() {
        guard musicEnabled, isSetUp else { return }
        if musicBuffer == nil {
            musicBuffer = makeBuffer(SynthSamples.musicLoop(sampleRate: format.sampleRate))
        }
        guard let musicBuffer else { return }
        if !engine.isRunning { startEngine() }
        guard engine.isRunning else { return }
        if !isMusicScheduled {
            musicPlayer.stop()
            musicPlayer.scheduleBuffer(musicBuffer, at: nil, options: .loops, completionHandler: nil)
            isMusicScheduled = true
        }
        if !musicPlayer.isPlaying { musicPlayer.play() }
    }

    func pauseMusic() {
        guard isSetUp else { return }
        musicPlayer.pause()
    }

    func stopMusic() {
        guard isSetUp else { return }
        musicPlayer.stop()
        isMusicScheduled = false
    }
}

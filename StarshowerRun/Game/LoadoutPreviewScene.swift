//
//  LoadoutPreviewScene.swift
//  StarshowerRun
//
//  A tiny live scene for the shop: your dot swooping around with its real
//  skin effects and blade trail over the selected theme.
//

import SpriteKit
import SwiftUI

final class LoadoutPreviewScene: SKScene {
    private var skinID: String
    private var trailID: String
    private var themeID: String

    private let background = SKSpriteNode()
    private let content = SKNode()
    private var avatar: PlayerAvatarNode?
    private var ribbon: RibbonTrailNode?
    private var hazards: [SKSpriteNode] = []
    private var lastUpdate: TimeInterval = 0
    private var time: Double = 0

    init(size: CGSize, skinID: String, trailID: String, themeID: String) {
        self.skinID = skinID
        self.trailID = trailID
        self.themeID = themeID
        super.init(size: size)
        scaleMode = .resizeFill
        background.zPosition = -10
        addChild(background)
        addChild(content)
        rebuild()
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func apply(skinID: String, trailID: String, themeID: String) {
        guard skinID != self.skinID || trailID != self.trailID || themeID != self.themeID else { return }
        self.skinID = skinID
        self.trailID = trailID
        self.themeID = themeID
        rebuild()
    }

    override func didChangeSize(_ oldSize: CGSize) {
        super.didChangeSize(oldSize)
        background.size = size
        background.position = CGPoint(x: size.width / 2, y: size.height / 2)
    }

    private func rebuild() {
        content.removeAllChildren()
        hazards.removeAll()
        let theme = Cosmetic.themeLook(themeID)
        backgroundColor = theme.backgroundBottom.uiColor
        background.texture = TextureFactory.verticalGradient(top: theme.backgroundTop, bottom: theme.backgroundBottom)
        background.size = size
        background.position = CGPoint(x: size.width / 2, y: size.height / 2)

        for index in 0..<4 {
            let color = index % 2 == 0 ? theme.hazard : theme.hazardAlt
            let node = SKSpriteNode(texture: TextureFactory.hazard(color: color, decoration: .plain, outline: false))
            let radius = CGFloat(8 + index * 3)
            node.size = CGSize(width: radius * 2 * TextureFactory.glowRatio, height: radius * 2 * TextureFactory.glowRatio)
            node.position = CGPoint(x: size.width * CGFloat([0.15, 0.85, 0.3, 0.7][index]),
                                    y: CGFloat.random(in: 0...max(size.height, 1)))
            node.alpha = 0.8
            content.addChild(node)
            hazards.append(node)
        }

        let avatar = PlayerAvatarNode(look: Cosmetic.skinLook(skinID))
        avatar.zPosition = 10
        avatar.setScale(1.3)
        avatar.setParticleTarget(content)
        let ribbon = RibbonTrailNode(look: Cosmetic.trailLook(trailID), playerRadius: CGFloat(GameEngine.basePlayerRadius) * 1.3)
        ribbon.zPosition = 9
        ribbon.ghostTexture = avatar.bodyTexture
        ribbon.ghostSize = CGSize(width: avatar.bodySize.width * 1.3, height: avatar.bodySize.height * 1.3)
        content.addChild(ribbon)
        content.addChild(avatar)
        ribbon.setParticleTarget(content)
        let start = pathPoint(at: 0)
        avatar.position = start
        ribbon.reset(at: start)
        self.avatar = avatar
        self.ribbon = ribbon
    }

    /// A lazy figure-eight across the lower-middle of the preview.
    private func pathPoint(at time: Double) -> CGPoint {
        let w = max(size.width, 1)
        let h = max(size.height, 1)
        return CGPoint(x: w / 2 + CGFloat(sin(time * 1.1)) * w * 0.3,
                       y: h * 0.55 + CGFloat(sin(time * 2.2)) * h * 0.18)
    }

    override func update(_ currentTime: TimeInterval) {
        let delta = lastUpdate > 0 ? min(currentTime - lastUpdate, 1.0 / 20.0) : 1.0 / 60.0
        lastUpdate = currentTime
        time += delta

        let head = pathPoint(at: time)
        avatar?.position = head
        avatar?.update(time: time)
        ribbon?.update(head: head, dt: delta, time: time, drift: 240)

        let height = max(size.height, 1)
        for (index, hazard) in hazards.enumerated() {
            hazard.position.y -= CGFloat(delta) * CGFloat(60 + index * 25)
            if hazard.position.y < -30 { hazard.position.y = height + 30 }
        }
    }
}

/// Owns the preview scene. `@StateObject`'s autoclosure runs once, so the
/// scene isn't rebuilt every time the parent view re-renders.
@MainActor
final class LoadoutPreviewHolder: ObservableObject {
    let scene: LoadoutPreviewScene

    init(skinID: String, trailID: String, themeID: String) {
        scene = LoadoutPreviewScene(size: CGSize(width: 360, height: 200),
                                    skinID: skinID, trailID: trailID, themeID: themeID)
    }
}

/// SwiftUI wrapper that keeps one preview scene alive and swaps looks in place.
struct LoadoutPreviewView: View {
    let skinID: String
    let trailID: String
    let themeID: String
    @StateObject private var holder: LoadoutPreviewHolder

    init(skinID: String, trailID: String, themeID: String) {
        self.skinID = skinID
        self.trailID = trailID
        self.themeID = themeID
        _holder = StateObject(wrappedValue: LoadoutPreviewHolder(skinID: skinID, trailID: trailID, themeID: themeID))
    }

    private var scene: LoadoutPreviewScene { holder.scene }

    var body: some View {
        SpriteView(scene: scene, preferredFramesPerSecond: 60)
            .onChange(of: skinID) { _, _ in refresh() }
            .onChange(of: trailID) { _, _ in refresh() }
            .onChange(of: themeID) { _, _ in refresh() }
    }

    private func refresh() {
        scene.apply(skinID: skinID, trailID: trailID, themeID: themeID)
    }
}

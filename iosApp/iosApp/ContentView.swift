import AVKit
import Combine
import SwiftUI
import UIKit
import ComposeApp

private let nuvioBackgroundColor = UIColor(
    red: 0.051,
    green: 0.051,
    blue: 0.051,
    alpha: 1.0
)

let nuvioPlayerImmersiveSystemUIVisibilityDidChange = Notification.Name(
    "NuvioPlayerImmersiveSystemUIVisibilityDidChange"
)
let nuvioPlayerImmersiveSystemUIVisibleKey = "isVisible"

private enum NuvioComposeHost {
    static let registerPlayerBridge: Void = {
        NuvioPlayerRegistration.register()
    }()

    static func wrap(
        _ contentController: UIViewController,
        disablesInteractiveContentPopGesture: Bool = false,
        passesThroughEmptyNavigationBarAreas: Bool = false,
        hidesContainingTabBar: Bool = false,
        onTabBarControllerAvailable: ((UITabBarController) -> Void)? = nil
    ) -> RootComposeViewController {
        _ = registerPlayerBridge
        contentController.view.backgroundColor = nuvioBackgroundColor
        return RootComposeViewController(
            contentController: contentController,
            disablesInteractiveContentPopGesture: disablesInteractiveContentPopGesture,
            passesThroughEmptyNavigationBarAreas: passesThroughEmptyNavigationBarAreas,
            hidesContainingTabBar: hidesContainingTabBar,
            onTabBarControllerAvailable: onTabBarControllerAvailable
        )
    }
}

final class RootComposeViewController: UIViewController {
    private let contentController: UIViewController
    private let disablesInteractiveContentPopGesture: Bool
    private let passesThroughEmptyNavigationBarAreas: Bool
    private let hidesContainingTabBar: Bool
    private let onTabBarControllerAvailable: ((UITabBarController) -> Void)?
    private var immersiveSystemUIObserver: NSObjectProtocol?

    init(
        contentController: UIViewController,
        disablesInteractiveContentPopGesture: Bool,
        passesThroughEmptyNavigationBarAreas: Bool = false,
        hidesContainingTabBar: Bool,
        onTabBarControllerAvailable: ((UITabBarController) -> Void)?
    ) {
        self.contentController = contentController
        self.disablesInteractiveContentPopGesture = disablesInteractiveContentPopGesture
        self.passesThroughEmptyNavigationBarAreas = passesThroughEmptyNavigationBarAreas
        self.hidesContainingTabBar = hidesContainingTabBar
        self.onTabBarControllerAvailable = onTabBarControllerAvailable
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        view.backgroundColor = nuvioBackgroundColor
        contentController.view.backgroundColor = nuvioBackgroundColor

        addChild(contentController)
        view.addSubview(contentController.view)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        contentController.didMove(toParent: self)

        immersiveSystemUIObserver = NotificationCenter.default.addObserver(
            forName: nuvioPlayerImmersiveSystemUIVisibilityDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.refreshImmersiveSystemUI()
        }
    }

    deinit {
        if let immersiveSystemUIObserver {
            NotificationCenter.default.removeObserver(immersiveSystemUIObserver)
        }
    }

    override var childForHomeIndicatorAutoHidden: UIViewController? {
        nil
    }

    override var childForScreenEdgesDeferringSystemGestures: UIViewController? {
        nil
    }

    override var childForStatusBarHidden: UIViewController? {
        nil
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        SystemUI.shared.activePlayer?.prefersHomeIndicatorAutoHidden
            ?? immersiveController(in: contentController)?.prefersHomeIndicatorAutoHidden
            ?? false
    }

    override var preferredScreenEdgesDeferringSystemGestures: UIRectEdge {
        SystemUI.shared.activePlayer?.preferredScreenEdgesDeferringSystemGestures
            ?? immersiveController(in: contentController)?.preferredScreenEdgesDeferringSystemGestures
            ?? []
    }

    override var prefersStatusBarHidden: Bool {
        SystemUI.shared.activePlayer?.prefersStatusBarHidden
            ?? immersiveController(in: contentController)?.prefersStatusBarHidden
            ?? false
    }

    override var preferredStatusBarStyle: UIStatusBarStyle {
        .lightContent
    }

    override var preferredStatusBarUpdateAnimation: UIStatusBarAnimation {
        .fade
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshContainingTabBarVisibility()
        refreshImmersiveSystemUI()
        setInteractiveContentPopGestureEnabled(false)
        setNavigationBarPassthroughEnabled(true)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        refreshContainingTabBarVisibility()
        refreshImmersiveSystemUI()
        setInteractiveContentPopGestureEnabled(false)
        if let tabBarController {
            onTabBarControllerAvailable?(tabBarController)
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        enforceContainingTabBarVisibility()
    }

    override func viewWillDisappear(_ animated: Bool) {
        setNavigationBarPassthroughEnabled(false)
        setInteractiveContentPopGestureEnabled(true)
        super.viewWillDisappear(animated)
    }

    func prepareForPictureInPictureParking() {
        setNavigationBarPassthroughEnabled(false)
        setInteractiveContentPopGestureEnabled(true)
    }

    func restoreAfterPictureInPictureParking() {
        refreshContainingTabBarVisibility()
        refreshImmersiveSystemUI()
        setInteractiveContentPopGestureEnabled(false)
        setNavigationBarPassthroughEnabled(true)
    }

    func refreshContainingTabBarVisibility() {
        enforceContainingTabBarVisibility()
        DispatchQueue.main.async { [weak self] in
            self?.enforceContainingTabBarVisibility()
        }
    }

    private func enforceContainingTabBarVisibility() {
        guard hidesContainingTabBar, let tabBar = tabBarController?.tabBar else { return }
        tabBar.isHidden = true
        tabBar.alpha = 0
        tabBar.isUserInteractionEnabled = false
    }

    func refreshImmersiveSystemUI() {
        var controller: UIViewController? = self
        while let current = controller {
            current.setNeedsUpdateOfHomeIndicatorAutoHidden()
            current.setNeedsUpdateOfScreenEdgesDeferringSystemGestures()
            current.setNeedsStatusBarAppearanceUpdate()
            controller = current.parent
        }
        SystemUI.shared.refresh()
    }

    private func setInteractiveContentPopGestureEnabled(_ enabled: Bool) {
        guard disablesInteractiveContentPopGesture else { return }
        if #available(iOS 26.0, *) {
            navigationController?.interactiveContentPopGestureRecognizer?.isEnabled = enabled
        }
    }

    private func setNavigationBarPassthroughEnabled(_ enabled: Bool) {
        guard passesThroughEmptyNavigationBarAreas else { return }
        NuvioNavigationBarPassthrough.setEnabled(enabled, on: navigationController)
    }

    private func immersiveController(in controller: UIViewController?) -> UIViewController? {
        guard let controller else { return nil }

        if controller.prefersHomeIndicatorAutoHidden ||
            !controller.preferredScreenEdgesDeferringSystemGestures.isEmpty ||
            controller.prefersStatusBarHidden {
            return controller
        }

        if let presented = immersiveController(in: controller.presentedViewController) {
            return presented
        }

        for child in controller.children.reversed() {
            if let immersiveChild = immersiveController(in: child) {
                return immersiveChild
            }
        }

        return nil
    }
}

// MARK: - UIKit fallback

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        NuvioComposeHost.wrap(MainViewControllerKt.MainViewController())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// MARK: - Native iOS navigation

@available(iOS 16.0, *)
extension AppRoute {
    var keepsTabBar: Bool { self is SettingsDestinationRoute }
}

extension Array where Element == RouteWrapper {
    var keepsTabBar: Bool { allSatisfy { $0.route.keepsTabBar } }
}

struct RouteWrapper: Hashable, Identifiable {
    let id = UUID()
    let route: AppRoute

    static func == (lhs: RouteWrapper, rhs: RouteWrapper) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

@available(iOS 16.0, *)
@MainActor
final class TabNavigationCoordinator: ObservableObject {
    @Published var path: [RouteWrapper] = [] {
        didSet { onPathChanged?(path.isEmpty) }
    }
    var onReturnedToRoot: (() -> Void)?
    var onPathChanged: ((Bool) -> Void)?

    func push(_ route: AppRoute, launchSingleTop: Bool) {
        if launchSingleTop,
           path.last?.route.navigationIdentity == route.navigationIdentity {
            AppKt.disposeRoute(route: route)
            return
        }
        path.append(RouteWrapper(route: route))
    }

    func pop() {
        guard !path.isEmpty else { return }
        var updatedPath = path
        updatedPath.removeLast()
        setPath(updatedPath)
    }

    func replace(_ route: AppRoute) {
        var updatedPath = path
        if updatedPath.isEmpty {
            updatedPath.append(RouteWrapper(route: route))
        } else {
            updatedPath[updatedPath.index(before: updatedPath.endIndex)] = RouteWrapper(route: route)
        }
        setPath(updatedPath)
    }

    func popToRoot() {
        setPath([])
    }

    func setPath(_ newPath: [RouteWrapper]) {
        let retainedIDs = Set(newPath.map(\.id))
        let removedRoutes = path
            .filter { !retainedIDs.contains($0.id) }
            .map(\.route)

        let returnedToRoot = !path.isEmpty && newPath.isEmpty
        path = newPath
        removedRoutes
            .filter { !PlayerPictureInPictureParking.shared.isParked($0) }
            .forEach { AppKt.disposeRoute(route: $0) }
        if returnedToRoot {
            onReturnedToRoot?()
        }
    }
}

@available(iOS 16.0, *)
enum NuvioAppTab: String, CaseIterable, Hashable {
    case home = "Home"
    case search = "Search"
    case discover = "Discover"
    case library = "Library"
    case calendar = "Calendar"
    case settings = "Settings"

    var fallbackTitle: String {
        String(localized: String.LocalizationValue(rawValue))
    }

    static func from(kotlinName: String?) -> NuvioAppTab? {
        switch kotlinName?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "home": return .home
        case "search": return .search
        case "discover": return .discover
        case "library": return .library
        case "calendar": return .calendar
        case "settings", "profile": return .settings
        default: return nil
        }
    }

    var iconAssetName: String {
        switch self {
        case .home: return "NuvioTabHome"
        case .search: return "NuvioTabSearch"
        case .discover: return "NuvioTabDiscover"
        case .calendar: return "NuvioTabCalendar"
        case .library: return "NuvioTabLibrary"
        case .settings: return "NuvioTabProfile"
        }
    }

    var fallbackSystemImage: String {
        switch self {
        case .home: return "house.fill"
        case .search: return "magnifyingglass"
        case .discover: return "safari.fill"
        case .calendar: return "calendar"
        case .library: return "rectangle.stack.fill"
        case .settings: return "person.crop.circle.fill"
        }
    }
}

private enum NuvioNativeTabIcon {
    private static let legacyStaticIconSize = CGSize(width: 25, height: 25)

    static func image(for tab: NuvioAppTab) -> UIImage {
        if let asset = UIImage(named: tab.iconAssetName) {
            return UIGraphicsImageRenderer(size: legacyStaticIconSize).image { _ in
                asset
                    .withRenderingMode(.alwaysOriginal)
                    .draw(in: CGRect(origin: .zero, size: legacyStaticIconSize))
            }.withRenderingMode(.alwaysTemplate)
        }

        return (UIImage(systemName: tab.fallbackSystemImage) ?? UIImage())
            .withRenderingMode(.alwaysTemplate)
    }

    static func gradientTinted(_ image: UIImage, colors: [UIColor]) -> UIImage {
        guard colors.count > 1, let mask = image.cgImage else { return image }
        let size = image.size
        guard size.width > 0, size.height > 0 else { return image }
        let cgColors = colors.map { $0.cgColor } as CFArray
        guard let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: cgColors,
            locations: nil
        ) else {
            return image
        }

        return UIGraphicsImageRenderer(size: size).image { context in
            let cgContext = context.cgContext
            let rect = CGRect(origin: .zero, size: size)
            cgContext.translateBy(x: 0, y: size.height)
            cgContext.scaleBy(x: 1, y: -1)
            cgContext.clip(to: rect, mask: mask)
            cgContext.drawLinearGradient(
                gradient,
                start: .zero,
                end: CGPoint(x: size.width, y: size.height),
                options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
            )
        }.withRenderingMode(.alwaysOriginal)
    }

    static func profileAvatar(
        name: String?,
        avatarColor: UIColor?,
        backgroundColor: UIColor?,
        avatarImage: UIImage?,
        selected: Bool,
        accent: UIColor
    ) -> UIImage {
        guard name != nil || avatarColor != nil || avatarImage != nil else {
            return image(for: .settings)
        }

        let size = CGSize(width: 28, height: 28)
        let baseColor = avatarColor
            ?? UIColor(red: 30 / 255, green: 136 / 255, blue: 229 / 255, alpha: 1)
        let fillColor = backgroundColor ?? baseColor.withAlphaComponent(0.15)
        let borderColor = selected ? accent : baseColor.withAlphaComponent(0.5)
        let initial = name?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .prefix(1)
            .uppercased() ?? ""

        return UIGraphicsImageRenderer(size: size).image { _ in
            let rect = CGRect(origin: .zero, size: size).insetBy(dx: 1, dy: 1)
            fillColor.setFill()
            UIBezierPath(ovalIn: rect).fill()

            if let avatarImage {
                UIBezierPath(ovalIn: rect).addClip()
                drawAspectFill(image: avatarImage, in: rect)
            } else if !initial.isEmpty {
                let font = UIFont.systemFont(ofSize: size.height * 0.45, weight: .bold)
                let attributes: [NSAttributedString.Key: Any] = [
                    .font: font,
                    .foregroundColor: baseColor,
                ]
                let textSize = initial.size(withAttributes: attributes)
                initial.draw(
                    at: CGPoint(
                        x: rect.midX - textSize.width / 2,
                        y: rect.midY - textSize.height / 2
                    ),
                    withAttributes: attributes
                )
            } else {
                image(for: .settings)
                    .withTintColor(baseColor, renderingMode: .alwaysOriginal)
                    .draw(in: rect.insetBy(dx: 5.5, dy: 5.5))
            }

            borderColor.setStroke()
            let borderPath = UIBezierPath(ovalIn: rect.insetBy(dx: 0.75, dy: 0.75))
            borderPath.lineWidth = 1.5
            borderPath.stroke()
        }.withRenderingMode(.alwaysOriginal)
    }

    private static func drawAspectFill(image: UIImage, in rect: CGRect) {
        guard image.size.width > 0, image.size.height > 0 else { return }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let drawSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        image.draw(
            in: CGRect(
                x: rect.midX - drawSize.width / 2,
                y: rect.midY - drawSize.height / 2,
                width: drawSize.width,
                height: drawSize.height
            )
        )
    }
}

@available(iOS 16.0, *)
final class NativeTabIconStore: ObservableObject {
    private static let chromeDidChange = Notification.Name("NuvioNativeTabChromeDidChange")
    private static let accentKey = "NuvioNativeTabAccentColor"
    private static let accentGradientKey = "NuvioNativeTabAccentGradient"
    private static let profileNameKey = "NuvioNativeProfileName"
    private static let profileColorKey = "NuvioNativeProfileAvatarColor"
    private static let profileURLKey = "NuvioNativeProfileAvatarURL"
    private static let profileBackgroundKey = "NuvioNativeProfileAvatarBackgroundColor"

    @Published private(set) var revision = 0
    @Published private(set) var accentColor = UIColor(
        red: 0.96,
        green: 0.96,
        blue: 0.96,
        alpha: 1
    )

    @Published private(set) var accentColors: [UIColor] = []

    func accentStyle(opacity: CGFloat = 1) -> AnyShapeStyle {
        let colors = accentColors.isEmpty ? [accentColor] : accentColors
        let faded = colors.map { Color(uiColor: $0).opacity(opacity) }
        guard faded.count > 1 else {
            return AnyShapeStyle(faded[0])
        }
        return AnyShapeStyle(
            LinearGradient(colors: faded, startPoint: .topLeading, endPoint: .bottomTrailing)
        )
    }

    private var observer: NSObjectProtocol?
    private var profileAvatarURL: String?
    private var profileAvatarImage: UIImage?
    private var profileAvatarTask: URLSessionDataTask?

    init() {
        UITabBar.appearance().unselectedItemTintColor = UIColor(
            red: 150 / 255,
            green: 156 / 255,
            blue: 163 / 255,
            alpha: 1
        )
        observer = NotificationCenter.default.addObserver(
            forName: Self.chromeDidChange,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.reload()
        }
        reload()
    }

    deinit {
        if let observer {
            NotificationCenter.default.removeObserver(observer)
        }
        profileAvatarTask?.cancel()
    }

    func image(for tab: NuvioAppTab, selected: Bool) -> UIImage {
        guard tab == .settings else {
            let icon = NuvioNativeTabIcon.image(for: tab)
            guard selected, accentColors.count > 1 else { return icon }
            return NuvioNativeTabIcon.gradientTinted(icon, colors: accentColors)
        }

        let defaults = UserDefaults.standard
        return NuvioNativeTabIcon.profileAvatar(
            name: defaults.string(forKey: Self.profileNameKey),
            avatarColor: UIColor(hexString: defaults.string(forKey: Self.profileColorKey)),
            backgroundColor: UIColor(hexString: defaults.string(forKey: Self.profileBackgroundKey)),
            avatarImage: profileAvatarImage,
            selected: selected,
            accent: accentColor
        )
    }

    private func reload() {
        let defaults = UserDefaults.standard
        accentColor = UIColor(hexString: defaults.string(forKey: Self.accentKey))
            ?? UIColor(red: 0.96, green: 0.96, blue: 0.96, alpha: 1)
        accentColors = (defaults.string(forKey: Self.accentGradientKey) ?? "")
            .split(separator: ",")
            .compactMap { UIColor(hexString: String($0).trimmingCharacters(in: .whitespaces)) }

        let nextURL = defaults.string(forKey: Self.profileURLKey)
        guard nextURL != profileAvatarURL else {
            revision &+= 1
            return
        }

        profileAvatarTask?.cancel()
        profileAvatarTask = nil
        profileAvatarURL = nextURL
        profileAvatarImage = nil
        revision &+= 1

        guard let nextURL, let url = URL(string: nextURL) else { return }
        profileAvatarTask = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data, let image = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                guard let self, self.profileAvatarURL == nextURL else { return }
                self.profileAvatarImage = image
                self.revision &+= 1
            }
        }
        profileAvatarTask?.resume()
    }
}

@available(iOS 16.0, *)
@MainActor
final class NativeProfileTabInteractionCoordinator: NSObject, UIGestureRecognizerDelegate {
    var onLongPress: (() -> Void)?
    private(set) var isHandlingLongPress = false
    private(set) var suppressesProfileSelection = false
    func setSuppressesProfileSelection(_ enabled: Bool) {
        suppressesProfileSelection = enabled
    }

    private weak var tabBarController: UITabBarController?
    private var selectedIndexBeforeLongPress: Int?
    private let competingRecognizers = NSHashTable<UIGestureRecognizer>.weakObjects()
    private lazy var recognizer: UILongPressGestureRecognizer = {
        let recognizer = UILongPressGestureRecognizer(
            target: self,
            action: #selector(handleLongPress(_:))
        )
        recognizer.minimumPressDuration = 0.45
        recognizer.cancelsTouchesInView = true
        recognizer.delegate = self
        return recognizer
    }()

    func attach(to tabBarController: UITabBarController) {
        guard self.tabBarController !== tabBarController else { return }
        self.tabBarController?.tabBar.removeGestureRecognizer(recognizer)
        tabBarController.tabBar.addGestureRecognizer(recognizer)
        self.tabBarController = tabBarController
        onAttached?()
    }

    var onAttached: (() -> Void)?

    func measureNativeTabBarMetrics() -> NuvioTabBarMetrics? {
        guard let tabBar = tabBarController?.tabBar,
              let window = tabBar.window,
              !tabBar.isHidden,
              tabBar.bounds.width > 0 else { return nil }
        let platterFrame: CGRect
        if let platter = Self.findPlatter(in: tabBar) {
            platterFrame = platter.convert(platter.bounds, to: window)
        } else if #available(iOS 17.0, *),
                  let items = tabBar.items, !items.isEmpty {
            let union = items
                .compactMap { $0.frame(in: tabBar) }
                .reduce(CGRect.null) { $0.union($1) }
            guard !union.isNull else { return nil }
            platterFrame = tabBar.convert(union.insetBy(dx: -4, dy: -4), to: window)
        } else {
            return nil
        }
        guard platterFrame.width > 0, platterFrame.height > 0 else { return nil }
        let (items, selectionFrame, selectionIndex) = Self.measureItems(
            in: tabBar,
            window: window,
            platterFrame: platterFrame
        )
        return NuvioTabBarMetrics(
            windowSize: window.bounds.size,
            leadingInset: platterFrame.minX,
            trailingInset: window.bounds.width - platterFrame.maxX,
            bottomInset: window.bounds.height - platterFrame.maxY,
            height: platterFrame.height,
            items: items,
            selectionFrame: selectionFrame,
            selectionItemIndex: selectionFrame == nil ? nil : selectionIndex
        )
    }

    private static func measureItems(
        in tabBar: UITabBar,
        window: UIWindow,
        platterFrame: CGRect
    ) -> ([NuvioTabBarItemMetrics], CGRect?, Int?) {
        func local(_ view: UIView) -> CGRect {
            view.convert(view.bounds, to: window).offsetBy(dx: -platterFrame.minX, dy: -platterFrame.minY)
        }
        func descendants(of view: UIView) -> [UIView] {
            view.subviews.flatMap { [$0] + descendants(of: $0) }
        }

        let buttons = descendants(of: tabBar)
            .filter { view in
                String(describing: type(of: view)).localizedCaseInsensitiveContains("TabBarButton") &&
                    !view.isHidden && view.alpha > 0.01 && view.bounds.width > 0
            }
            .sorted { local($0).minX < local($1).minX }
        guard !buttons.isEmpty else { return ([], nil, nil) }

        var items: [NuvioTabBarItemMetrics] = []
        for button in buttons {
            let inner = descendants(of: button).filter { !$0.isHidden && $0.alpha > 0.01 }
            guard let imageView = inner
                .compactMap({ $0 as? UIImageView })
                .filter({ $0.image != nil })
                .max(by: { $0.bounds.width * $0.bounds.height < $1.bounds.width * $1.bounds.height })
            else { return ([], nil, nil) }
            let label = inner.compactMap { $0 as? UILabel }.first { !($0.text ?? "").isEmpty }
            items.append(
                NuvioTabBarItemMetrics(
                    buttonFrame: local(button),
                    iconFrame: local(imageView),
                    labelFrame: label.map(local),
                    labelFont: label?.font
                )
            )
        }

        var selectionFrame: CGRect?
        var selectionIndex: Int?
        if let selectedItem = tabBar.selectedItem,
           let selectedIndex = tabBar.items?.firstIndex(of: selectedItem),
           selectedIndex < buttons.count {
            selectionIndex = selectedIndex
            let buttonFrame = local(buttons[selectedIndex])
            let center = CGPoint(x: buttonFrame.midX, y: buttonFrame.midY)
            let buttonSet = Set(buttons.flatMap { [ObjectIdentifier($0)] + descendants(of: $0).map(ObjectIdentifier.init) })
            selectionFrame = descendants(of: tabBar)
                .filter { view in
                    guard !buttonSet.contains(ObjectIdentifier(view)),
                          !view.isHidden, view.alpha > 0.01 else { return false }
                    let frame = local(view)
                    return frame.contains(center) &&
                        frame.width >= buttonFrame.width * 0.8 &&
                        frame.width <= buttonFrame.width * 1.8 &&
                        frame.height >= platterFrame.height * 0.6 &&
                        frame.height < platterFrame.height + 1
                }
                .map(local)
                .min { $0.width * $0.height < $1.width * $1.height }
        }
        return (items, selectionFrame, selectionIndex)
    }

    private static func findPlatter(in view: UIView) -> UIView? {
        var best: UIView?
        var stack = view.subviews
        while let current = stack.popLast() {
            let name = String(describing: type(of: current))
            if name.localizedCaseInsensitiveContains("Platter"),
               !current.isHidden,
               current.bounds.width > (best?.bounds.width ?? 0) {
                best = current
            }
            stack.append(contentsOf: current.subviews)
        }
        return best
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        // Better Nuvio: sem ação de toque longo (o último item não é mais Perfil), o
        // reconhecedor não pode receber toques: dava háptico, não fazia nada e desfazia a seleção.
        guard gestureRecognizer === recognizer,
              onLongPress != nil,
              let tabBar = tabBarController?.tabBar,
              let profileItem = tabBar.items?.last else {
            return false
        }
        competingRecognizers.removeAllObjects()
        guard #available(iOS 17.0, *),
              let profileFrame = profileItem.frame(in: tabBar) else { return false }
        return profileFrame.contains(touch.location(in: tabBar))
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        guard gestureRecognizer === recognizer || otherGestureRecognizer === recognizer else {
            return false
        }
        competingRecognizers.add(
            gestureRecognizer === recognizer ? otherGestureRecognizer : gestureRecognizer
        )
        return true
    }

    @objc private func handleLongPress(_ recognizer: UILongPressGestureRecognizer) {
        switch recognizer.state {
        case .began:
            selectedIndexBeforeLongPress = tabBarController?.selectedIndex
            isHandlingLongPress = true
            suppressesProfileSelection = true
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onLongPress?()
            competingRecognizers.allObjects.forEach { competingRecognizer in
                competingRecognizer.isEnabled = false
                competingRecognizer.isEnabled = true
            }
            if let selectedIndexBeforeLongPress {
                tabBarController?.selectedIndex = selectedIndexBeforeLongPress
            }
        case .ended, .cancelled, .failed:
            let selectedIndex = selectedIndexBeforeLongPress
            isHandlingLongPress = false
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                if let selectedIndex {
                    self.tabBarController?.selectedIndex = selectedIndex
                }
                self.selectedIndexBeforeLongPress = nil
                self.suppressesProfileSelection = false
            }
        default:
            break
        }
    }
}

@available(iOS 16.0, *)
@MainActor
final class AppNavigationCoordinator: ObservableObject {
    private static let nativeTabBarVisibleKey = "NuvioNativeTabBarVisible"

    @Published var selectedTab: NuvioAppTab = .home {
        didSet {
            if selectedTab != .settings {
                lastBarTab = selectedTab
            }
            if selectedTab != oldValue {
                setTabBarVisible(true)
                refreshSelectedTabDepth()
                NativeTabBridgeKt.nativeTabVisibilityChanged(tabName: selectedTab.rawValue)
            }
        }
    }
    @Published private(set) var isMainContentMounted = false
    @Published private(set) var isMainContentVisible = false
    @Published private(set) var isAppReady = false
    @Published private(set) var isTabBarVisible = true
    @Published private(set) var isNativeTabBarVisible = true
    @Published private(set) var isCompactPillHidden = true
    @Published private(set) var nativeTabBarMetricsBySize: [String: NuvioTabBarMetrics] = [:]

    func nativeTabBarMetrics(for size: CGSize) -> NuvioTabBarMetrics? {
        nativeTabBarMetricsBySize[NuvioTabBarMetrics.key(for: size)]
    }

    func refreshNativeTabBarMetrics() {
        guard isNativeTabBarVisible,
              let metrics = profileTabInteraction.measureNativeTabBarMetrics() else { return }
        let key = NuvioTabBarMetrics.key(for: metrics.windowSize)
        guard nativeTabBarMetricsBySize[key] != metrics else { return }
        nativeTabBarMetricsBySize[key] = metrics
    }
    @Published private(set) var tabBarBehavior: NuvioTabBarBehavior = NuvioTabBarBehavior.current()
    @Published private(set) var isSelectedTabAtRoot = true
    @Published private var localizedTabTitles: [NuvioAppTab: String] = [:]

    private var tabBarTransitionTask: Task<Void, Never>?

    let homeCoordinator = TabNavigationCoordinator()
    let searchCoordinator = TabNavigationCoordinator()
    let libraryCoordinator = TabNavigationCoordinator()
    let discoverCoordinator = TabNavigationCoordinator()
    let calendarCoordinator = TabNavigationCoordinator()
    let settingsCoordinator = TabNavigationCoordinator()
    let appGateController = AppGateController()
    let profileSwitcherController = NativeProfileSwitcherController()
    let profileTabInteraction = NativeProfileTabInteractionCoordinator()

    init() {
        PlayerPictureInPictureParking.shared.appCoordinator = self
        setTabBarVisible(true)
        reloadTabBarBehavior()
        NativeTabBridgeKt.observeNativePopToRoot { [weak self] tabName in
            guard let self, let tab = NuvioAppTab.from(kotlinName: tabName) else { return }
            self.coordinator(for: tab).popToRoot()
            self.selectedTab = tab
        }
        NativeTabBridgeKt.observeNativeTabBarVisible { [weak self] visible in
            guard let self else { return }
            self.setTabBarVisible(visible.boolValue, animated: self.tabBarBehavior == .morphed)
        }
        allCoordinators.forEach { coordinator in
            coordinator.onReturnedToRoot = { [weak self] in
                self?.setTabBarVisible(true)
            }
            coordinator.onPathChanged = { [weak self] _ in
                self?.refreshSelectedTabDepth()
            }
        }
        profileTabInteraction.onAttached = { [weak self] in
            DispatchQueue.main.async { self?.refreshNativeTabBarMetrics() }
        }
        // Better Nuvio: the last bar item is no longer Perfil, so its long-press opens nothing.
        profileTabInteraction.onLongPress = nil
    }

    private var allCoordinators: [TabNavigationCoordinator] {
        [
            homeCoordinator, searchCoordinator, discoverCoordinator, libraryCoordinator,
            calendarCoordinator, settingsCoordinator,
        ]
    }

    /// Tabs shown in the bar: Início, Busca, Explorar, Biblioteca, Agenda (TV names).
    /// Perfil/Ajustes is not a tab anymore: it opens as a sheet from the avatar on Home.
    /// Better Nuvio: Live TV saiu do app (como na TV 1.6.11).
    var availableTabs: [NuvioAppTab] {
        NuvioAppTab.allCases.filter { $0 != .settings }
    }

    /// Last bar tab the user was on, shown underneath the Settings sheet.
    @Published private(set) var lastBarTab: NuvioAppTab = .home

    /// Better Nuvio: com a folha de Ajustes aberta, selectedTab == .settings, mas a aba de
    /// baixo (lastBarTab) continua visível atrás da folha e não pode virar Color.clear.
    func isTabOnScreen(_ tab: NuvioAppTab) -> Bool {
        selectedTab == tab || (selectedTab == .settings && lastBarTab == tab)
    }

    /// Tab highlighted in the bar: the one under the Settings sheet while it is open.
    var highlightedBarTab: NuvioAppTab {
        selectedTab == .settings ? lastBarTab : selectedTab
    }

    var isSettingsPresented: Bool {
        get { selectedTab == .settings }
        set {
            if !newValue && selectedTab == .settings {
                selectedTab = lastBarTab
            }
        }
    }

    var usesGlassTabBarOverlay: Bool {
        tabBarBehavior.usesCompactPill ||
            (tabBarBehavior.isEnabled && UIDevice.current.userInterfaceIdiom == .pad)
    }

    func reloadTabBarBehavior() {
        let behavior = NuvioTabBarBehavior.current()
        guard tabBarBehavior != behavior else { return }
        tabBarBehavior = behavior
        if !behavior.respondsToScroll {
            setTabBarVisible(true)
        }
    }

    private func refreshSelectedTabDepth() {
        let atRoot = coordinator(for: selectedTab).path.keepsTabBar
        if isSelectedTabAtRoot != atRoot {
            isSelectedTabAtRoot = atRoot
        }
    }

    func requestTabBarVisible(_ visible: Bool) {
        setTabBarVisible(visible, animated: tabBarBehavior == .morphed)
    }

    func reloadTabBarVisibility() {
        guard tabBarBehavior.respondsToScroll else {
            setTabBarVisible(true)
            return
        }
        guard let visible = UserDefaults.standard.object(
            forKey: Self.nativeTabBarVisibleKey
        ) as? Bool else {
            return
        }
        setTabBarVisible(visible, animated: tabBarBehavior == .morphed)
    }

    private func setTabBarVisible(_ visible: Bool, animated: Bool = false) {
        UserDefaults.standard.set(visible, forKey: Self.nativeTabBarVisibleKey)

        tabBarTransitionTask?.cancel()
        tabBarTransitionTask = nil

        guard animated else {
            isTabBarVisible = visible
            isNativeTabBarVisible = visible
            isCompactPillHidden = visible
            return
        }

        isNativeTabBarVisible = false
        isCompactPillHidden = false
        guard isTabBarVisible != visible else { return }
        isTabBarVisible = visible
    }

    func coordinator(for tab: NuvioAppTab) -> TabNavigationCoordinator {
        switch tab {
        case .home: return homeCoordinator
        case .search: return searchCoordinator
        case .discover: return discoverCoordinator
        case .library: return libraryCoordinator
        case .calendar: return calendarCoordinator
        case .settings: return settingsCoordinator
        }
    }

    func activateTab(named tabName: String) {
        guard let tab = NuvioAppTab.from(kotlinName: tabName) else { return }
        if tab == .home || isAppReady {
            selectedTab = tab
        }
    }

    func title(for tab: NuvioAppTab) -> String {
        localizedTabTitles[tab] ?? tab.fallbackTitle
    }

    func updateTabTitles(
        home: String,
        search: String,
        library: String,
        profile: String,
        switchProfile: String,
        addProfile: String,
        discover: String,
        calendar: String
    ) {
        localizedTabTitles = [
            .home: home,
            .search: search,
            .discover: discover,
            .library: library,
            .calendar: calendar,
            .settings: profile,
        ]
    }

    func updateAppReady(_ ready: Bool) {
        isAppReady = ready
        if !ready {
            setTabBarVisible(true)
            selectedTab = .home
            allCoordinators.forEach { $0.popToRoot() }
        }
    }

    func setMainContentMounted(_ mounted: Bool) {
        isMainContentMounted = mounted
        if !mounted {
            isMainContentVisible = false
            selectedTab = .home
        }
    }

    func setMainContentVisible(_ visible: Bool) {
        isMainContentVisible = visible
    }

    func tab(for target: TabNavigationCoordinator) -> NuvioAppTab? {
        NuvioAppTab.allCases.first { coordinator(for: $0) === target }
    }

    func push(
        _ route: AppRoute,
        from origin: TabNavigationCoordinator,
        launchSingleTop: Bool
    ) {
        guard isAppReady else {
            AppKt.disposeRoute(route: route)
            return
        }
        if route is PlayerRoute, !PlayerPictureInPictureParking.shared.isParked(route) {
            PlayerPictureInPictureParking.shared.closeParked()
        }
        let requestedTab = NuvioAppTab.from(kotlinName: route.preferredTabName)
            ?? tab(for: origin)
            ?? selectedTab
        var targetTab = requestedTab
        // Better Nuvio: um título (ou fontes, player, catálogo) aberto de dentro dos Ajustes
        // fecha a folha e abre na aba de baixo, em vez de ficar preso dentro da folha.
        if targetTab == .settings && Self.opensOutsideSettingsSheet(route) {
            targetTab = lastBarTab
        }
        let target = coordinator(for: targetTab)
        selectedTab = targetTab
        target.push(route, launchSingleTop: launchSingleTop)
    }

    private static func opensOutsideSettingsSheet(_ route: AppRoute) -> Bool {
        route is DetailRoute || route is StreamRoute || route is PlayerRoute ||
            route is CatalogRoute || route is PersonDetailRoute ||
            route is EntityBrowseRoute || route is FolderDetailRoute
    }

    func showParkedPlayerRoute(_ route: AppRoute, in target: TabNavigationCoordinator) {
        if let targetTab = tab(for: target) {
            selectedTab = targetTab
        }
        target.path.append(RouteWrapper(route: route))
    }

    func replace(_ route: AppRoute, in target: TabNavigationCoordinator) {
        guard isAppReady else {
            AppKt.disposeRoute(route: route)
            return
        }
        if let targetTab = tab(for: target) {
            selectedTab = targetTab
        }
        target.replace(route)
    }
}

@available(iOS 16.0, *)
struct NativeNavComposeView: UIViewControllerRepresentable {
    let tab: NuvioAppTab
    let usesNativeTabBar: Bool
    let usesTabletFloatingTabBar: Bool
    let coordinator: TabNavigationCoordinator
    let appCoordinator: AppNavigationCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(
            initialTabName: tab.rawValue,
            useNativeTabBar: usesNativeTabBar,
            useTabletFloatingTabBar: usesTabletFloatingTabBar,
            onNavigate: { route, launchSingleTop in
                appCoordinator.push(
                    route,
                    from: coordinator,
                    launchSingleTop: launchSingleTop.boolValue
                )
            },
            onGoBack: {
                coordinator.pop()
            },
            onReplace: { route in
                appCoordinator.replace(route, in: coordinator)
            },
            onActivate: { tabName in
                appCoordinator.activateTab(named: tabName)
            },
            onTabTitles: { home, search, library, profile, switchProfile, addProfile, discover, calendar in
                appCoordinator.updateTabTitles(
                    home: home,
                    search: search,
                    library: library,
                    profile: profile,
                    switchProfile: switchProfile,
                    addProfile: addProfile,
                    discover: discover,
                    calendar: calendar
                )
            },
            appGateController: appCoordinator.appGateController
        )
        return NuvioComposeHost.wrap(
            controller,
            hidesContainingTabBar: !usesNativeTabBar,
            onTabBarControllerAvailable: { tabBarController in
                if usesNativeTabBar {
                    appCoordinator.profileTabInteraction.attach(to: tabBarController)
                }
            }
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        (uiViewController as? RootComposeViewController)?.refreshContainingTabBarVisibility()
    }
}

@available(iOS 16.0, *)
struct AppGateComposeView: UIViewControllerRepresentable {
    let appCoordinator: AppNavigationCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.AppGateViewController(
            appGateController: appCoordinator.appGateController,
            nativeProfileSwitcherController: appCoordinator.profileSwitcherController,
            onActivate: { tabName in
                appCoordinator.activateTab(named: tabName)
            },
            onAppReady: { ready in
                appCoordinator.updateAppReady(ready.boolValue)
            },
            onMainContentMountChanged: { mounted in
                appCoordinator.setMainContentMounted(mounted.boolValue)
            },
            onMainContentVisibleChanged: { visible in
                appCoordinator.setMainContentVisible(visible.boolValue)
            }
        )
        controller.view.backgroundColor = .clear
        controller.view.isOpaque = false
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@available(iOS 16.0, *)
struct DetailComposeView: UIViewControllerRepresentable {
    let route: AppRoute
    let usesNativeTabBar: Bool
    let usesTabletFloatingTabBar: Bool
    let coordinator: TabNavigationCoordinator
    let appCoordinator: AppNavigationCoordinator

    func makeUIViewController(context: Context) -> UIViewController {
        if route is PlayerRoute {
            return PlayerRouteContainerViewController(
                route: route,
                coordinator: coordinator,
                makeContent: { makeScreenController() }
            )
        }
        return makeScreenController()
    }

    private func makeScreenController() -> UIViewController {
        let route = route
        let coordinator = coordinator
        let appCoordinator = appCoordinator
        let parking = PlayerPictureInPictureParking.shared
        let controller = MainViewControllerKt.ScreenViewController(
            route: route,
            onNavigate: { newRoute, launchSingleTop in
                appCoordinator.push(
                    newRoute,
                    from: coordinator,
                    launchSingleTop: launchSingleTop.boolValue
                )
            },
            onGoBack: {
                if parking.isParked(route) {
                    parking.closeParked()
                } else {
                    coordinator.pop()
                }
            },
            onReplace: { newRoute in
                if parking.isParked(route) {
                    AppKt.disposeRoute(route: newRoute)
                    return
                }
                appCoordinator.replace(newRoute, in: coordinator)
            },
            onActivate: { tabName in
                appCoordinator.activateTab(named: tabName)
            },
            appGateController: appCoordinator.appGateController,
            useNativeTabBar: usesNativeTabBar,
            useTabletFloatingTabBar: usesTabletFloatingTabBar
        )
        return NuvioComposeHost.wrap(
            controller,
            disablesInteractiveContentPopGesture: true,
            passesThroughEmptyNavigationBarAreas: true
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@available(iOS 16.0, *)
struct TabContentView: View {
    let tab: NuvioAppTab
    let usesNativeTabBar: Bool
    let usesTabletFloatingTabBar: Bool
    @ObservedObject var coordinator: TabNavigationCoordinator
    @ObservedObject var appCoordinator: AppNavigationCoordinator

    var body: some View {
        NavigationStack(
            path: Binding(
                get: { coordinator.path },
                set: { coordinator.setPath($0) }
            )
        ) {
            NativeNavComposeView(
                tab: tab,
                usesNativeTabBar: usesNativeTabBar,
                usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                coordinator: coordinator,
                appCoordinator: appCoordinator
            )
            .ignoresSafeArea(.all)
            .navigationTitle(appCoordinator.title(for: tab))
            .navigationBarHidden(true)
            .navigationDestination(for: RouteWrapper.self) { wrapper in
                if appCoordinator.isTabOnScreen(tab) {
                    DetailDestinationView(
                        wrapper: wrapper,
                        usesNativeTabBar: usesNativeTabBar,
                        usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                        coordinator: coordinator,
                        appCoordinator: appCoordinator
                    )
                    .id(wrapper.id)
                } else {
                    Color.clear
                }
            }
        }
        .toolbar(
            usesNativeTabBar &&
                appCoordinator.isMainContentVisible &&
                coordinator.path.keepsTabBar &&
                !appCoordinator.usesGlassTabBarOverlay &&
                appCoordinator.isNativeTabBarVisible
                ? Visibility.visible
                : Visibility.hidden,
            for: .tabBar
        )
        .animation(
            appCoordinator.tabBarBehavior == .autoHide
                ? .easeInOut(duration: 0.18)
                : nil,
            value: appCoordinator.isNativeTabBarVisible
        )
    }
}

@available(iOS 16.0, *)
private struct NativeToolbarReadabilityFade: View {
    var body: some View {
        Rectangle()
            .fill(
                LinearGradient(
                    stops: [
                        .init(color: Color(uiColor: nuvioBackgroundColor), location: 0),
                        .init(color: Color(uiColor: nuvioBackgroundColor).opacity(0.78), location: 0.55),
                        .init(color: Color(uiColor: nuvioBackgroundColor).opacity(0), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .frame(height: 120)
            .ignoresSafeArea(edges: .top)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}

@available(iOS 16.0, *)
private struct DetailDestinationView: View {
    let wrapper: RouteWrapper
    let usesNativeTabBar: Bool
    let usesTabletFloatingTabBar: Bool
    @ObservedObject var coordinator: TabNavigationCoordinator
    @ObservedObject var appCoordinator: AppNavigationCoordinator

    private var usesComposeNavigationHeader: Bool {
        wrapper.route is DetailRoute || wrapper.route is StreamRoute
    }

    private var respectsNativeNavigationSafeArea: Bool {
        wrapper.route is FolderDetailRoute
    }

    private var showsReadabilityFade: Bool {
        !wrapper.route.hidesNavigationBar && !usesComposeNavigationHeader
    }

    private var content: some View {
        ZStack(alignment: .top) {
            DetailComposeView(
                route: wrapper.route,
                usesNativeTabBar: usesNativeTabBar,
                usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                coordinator: coordinator,
                appCoordinator: appCoordinator
            )
            .ignoresSafeArea(
                .all,
                edges: respectsNativeNavigationSafeArea ? [.horizontal, .bottom] : .all
            )

            if showsReadabilityFade {
                NativeToolbarReadabilityFade()
            }
        }
        .navigationTitle(wrapper.route.title ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarRole(usesComposeNavigationHeader ? .editor : .automatic)
        .toolbar {
            if usesComposeNavigationHeader {
                ToolbarItem(placement: .principal) {
                    Color.clear.frame(width: 1, height: 1)
                }
            }
        }
        .toolbar(
            usesNativeTabBar &&
                wrapper.route.keepsTabBar &&
                !appCoordinator.usesGlassTabBarOverlay &&
                appCoordinator.isNativeTabBarVisible
                ? Visibility.visible
                : Visibility.hidden,
            for: .tabBar
        )
        .toolbar(
            wrapper.route.hidesNavigationBar ? Visibility.hidden : Visibility.visible,
            for: .navigationBar
        )
    }

    @ViewBuilder
    var body: some View {
        if #available(iOS 26.0, *), !usesComposeNavigationHeader {
            content.navigationSubtitle(wrapper.route.subtitle ?? "")
        } else {
            content
        }
    }
}

@available(iOS 16.0, *)
struct NativeNavContentView: View {
    @StateObject private var appCoordinator = AppNavigationCoordinator()
    @StateObject private var iconStore = NativeTabIconStore()

    private var usesNativeTabBar: Bool {
        let idiom = UIDevice.current.userInterfaceIdiom
        guard idiom == .phone || idiom == .pad else {
            return false
        }
        if #available(iOS 26.0, *) {
            return appCoordinator.tabBarBehavior.isEnabled
        }
        return false
    }

    private var usesTabletFloatingTabBar: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }

    private var tabSelection: Binding<NuvioAppTab> {
        Binding(
            get: {
                appCoordinator.selectedTab == .settings
                    ? appCoordinator.lastBarTab
                    : appCoordinator.selectedTab
            },
            set: { newTab in
                if newTab == .settings &&
                    appCoordinator.profileTabInteraction.suppressesProfileSelection {
                    return
                }
                if newTab == appCoordinator.selectedTab {
                    NativeTabBridgeKt.nativeTabSelect(tabName: newTab.rawValue)
                    return
                }
                if appCoordinator.isAppReady || newTab == .home {
                    appCoordinator.selectedTab = newTab
                }
            }
        )
    }

    @State private var mountedLegacyTabs: Set<NuvioAppTab> = []

    private var legacyTabs: some View {
        ZStack {
            ForEach(appCoordinator.availableTabs, id: \.self) { tab in
                if mountedLegacyTabs.contains(tab) {
                    TabContentView(
                        tab: tab,
                        usesNativeTabBar: usesNativeTabBar,
                        usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                        coordinator: appCoordinator.coordinator(for: tab),
                        appCoordinator: appCoordinator
                    )
                    .opacity(appCoordinator.isTabOnScreen(tab) ? 1 : 0)
                    .allowsHitTesting(appCoordinator.selectedTab == tab)
                    .accessibilityHidden(appCoordinator.selectedTab != tab)
                    .zIndex(appCoordinator.isTabOnScreen(tab) ? 1 : 0)
                }
            }
        }
        .onAppear {
            mountedLegacyTabs.insert(appCoordinator.selectedTab)
        }
        .onChange(of: appCoordinator.selectedTab) { newTab in
            mountedLegacyTabs.insert(newTab)
        }
    }

    @available(iOS 26.0, *)
    private var nativeTabs: some View {
        TabView(selection: tabSelection) {
            ForEach(appCoordinator.availableTabs, id: \.self) { tab in
                Tab(value: tab) {
                    TabContentView(
                        tab: tab,
                        usesNativeTabBar: usesNativeTabBar,
                        usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                        coordinator: appCoordinator.coordinator(for: tab),
                        appCoordinator: appCoordinator
                    )
                } label: {
                    Label {
                        Text(appCoordinator.title(for: tab))
                    } icon: {
                        Image(
                            uiImage: iconStore.image(
                                for: tab,
                                selected: appCoordinator.highlightedBarTab == tab
                            )
                        )
                        .id(
                            "\(tab.rawValue)-\(iconStore.revision)-" +
                                "\(appCoordinator.highlightedBarTab == tab)"
                        )
                    }
                }
            }
        }
        .tint(Color(uiColor: iconStore.accentColor))
        .tabBarMinimizeBehavior(
            appCoordinator.tabBarBehavior == .autoHide ? .onScrollDown : .never
        )
        .overlay(alignment: .bottom) {
            if appCoordinator.usesGlassTabBarOverlay &&
                appCoordinator.isAppReady &&
                appCoordinator.isSelectedTabAtRoot {
                GeometryReader { proxy in
                    NuvioGlassTabBar(
                        appCoordinator: appCoordinator,
                        iconStore: iconStore
                    )
                    .frame(width: proxy.size.width)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
                }
                .ignoresSafeArea(.all)
            }
        }
        .ignoresSafeArea(.container, edges: .bottom)
    }

    @ViewBuilder
    var body: some View {
        ZStack {
            Group {
                if appCoordinator.isMainContentMounted {
                    if #available(iOS 26.0, *), usesNativeTabBar {
                        nativeTabs
                    } else {
                        legacyTabs
                    }
                } else {
                    Color(uiColor: nuvioBackgroundColor)
                        .ignoresSafeArea(.all)
                }
            }
            .zIndex(0)
            .sheet(isPresented: $appCoordinator.isSettingsPresented) {
                TabContentView(
                    tab: .settings,
                    usesNativeTabBar: true,
                    usesTabletFloatingTabBar: usesTabletFloatingTabBar,
                    coordinator: appCoordinator.coordinator(for: .settings),
                    appCoordinator: appCoordinator
                )
                .ignoresSafeArea(.all)
                // Better Nuvio: fundo da folha dos Ajustes = #0A0A0B (sem o material azulado do sistema).
                .background(Color(red: 10 / 255, green: 10 / 255, blue: 11 / 255).ignoresSafeArea())
                // A tela Compose segura o arrastar para baixo, então a folha precisa de um X para fechar.
                .overlay(alignment: .topTrailing) {
                    BetterNuvioSheetCloseButton(coordinator: appCoordinator.coordinator(for: .settings)) {
                        appCoordinator.isSettingsPresented = false
                    }
                    .padding(.top, 20)
                    .padding(.trailing, 16)
                }
                .presentationDragIndicator(.visible)
                .modifier(BetterNuvioSheetBackground())
            }

            AppGateComposeView(appCoordinator: appCoordinator)
                .ignoresSafeArea(.all)
                .allowsHitTesting(!appCoordinator.isAppReady)
                .accessibilityHidden(appCoordinator.isAppReady)
                .zIndex(1)
        }
        .onReceive(
            NotificationCenter.default.publisher(
                for: Notification.Name("NuvioNativeTabChromeDidChange")
            )
        ) { _ in
            appCoordinator.reloadTabBarBehavior()
            appCoordinator.reloadTabBarVisibility()
        }
    }
}

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @ObservedObject private var systemUI = SystemUI.shared

    var body: some View {
        Group {
            if #available(iOS 16.0, *) {
                NativeNavContentView()
                    .accessibilityHidden(systemUI.pictureInPictureMessage != nil)
                    .allowsHitTesting(systemUI.pictureInPictureMessage == nil)
            } else {
                ComposeView()
                    .ignoresSafeArea(.all)
                    .accessibilityHidden(systemUI.pictureInPictureMessage != nil)
                    .allowsHitTesting(systemUI.pictureInPictureMessage == nil)
            }
        }
        .persistentSystemOverlays(systemUI.isPlayerImmersive ? .hidden : .automatic)
        .statusBarHidden(systemUI.isPlayerImmersive)
        .overlay {
            if let message = systemUI.pictureInPictureMessage {
                Color.black
                    .ignoresSafeArea()
                    .overlay {
                        VStack(spacing: 16) {
                            Image(uiImage: AVPictureInPictureController.pictureInPictureButtonStartImage)
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 96, height: 72)
                                .accessibilityHidden(true)
                            Text(message)
                                .font(.body)
                                .multilineTextAlignment(.center)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .foregroundStyle(.gray)
                        .padding(24)
                        .accessibilityElement(children: .combine)
                    }
            }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                DispatchQueue.main.async {
                    NotificationCenter.default.post(name: playerSceneDidActivateNotification, object: nil)
                }
            }
        }
    }
}

private extension UIColor {
    convenience init?(hexString: String?) {
        guard var value = hexString?.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.isEmpty else {
            return nil
        }
        if value.hasPrefix("#") {
            value.removeFirst()
        }
        guard value.count == 6, let rgb = UInt64(value, radix: 16) else {
            return nil
        }
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255,
            green: CGFloat((rgb >> 8) & 0xFF) / 255,
            blue: CGFloat(rgb & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// X redondo no canto da folha dos Ajustes, só na raiz: as páginas de dentro têm a própria barra.
private struct BetterNuvioSheetCloseButton: View {
    @ObservedObject var coordinator: TabNavigationCoordinator
    let action: () -> Void

    var body: some View {
        if coordinator.path.isEmpty {
            button
        }
    }

    private static var closeTitle: String {
        let title = NativeTabBridgeKt.nativeSheetCloseTitle()
        return title.isEmpty ? NSLocalizedString("Close", comment: "Close the Settings sheet") : title
    }

    private var button: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 36, height: 36)
                .background(Circle().fill(Color.white.opacity(0.14)))
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(Self.closeTitle))
    }
}

/// Fundo da folha dos Ajustes sem o material do sistema (iOS 16.4+).
private struct BetterNuvioSheetBackground: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 16.4, *) {
            content.presentationBackground(Color(red: 10 / 255, green: 10 / 255, blue: 11 / 255))
        } else {
            content
        }
    }
}

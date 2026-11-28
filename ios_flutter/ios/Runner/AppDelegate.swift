import AVFoundation
import CoreLocation
import Flutter
import PhotosUI
import UIKit
import Vision
import WebKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  private var locationBridge: CampusLocationBridge?
  private var activeScanner: CampusQrScannerController?
  private var ocrBridge: CampusOfflineOcrBridge?

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    let messenger = engineBridge.applicationRegistrar.messenger()

    // 1. WKHTTPCookieStore 原生桥接：完整读取/注入 HttpOnly Cookie（保留真实 domain/path）。
    let cookieChannel = FlutterMethodChannel(
      name: "com.ecut.campuspocket/wk_cookies",
      binaryMessenger: messenger
    )
    cookieChannel.setMethodCallHandler { call, result in
      switch call.method {
      case "getAllCookies":
        WKWebsiteDataStore.default().httpCookieStore.getAllCookies { cookies in
          let mapped: [[String: Any]] = cookies.map { cookie in
            let expiresMs: Int64 = cookie.expiresDate.map {
              Int64($0.timeIntervalSince1970 * 1000.0)
            } ?? 0
            return [
              "domain": cookie.domain,
              "name": cookie.name,
              "value": cookie.value,
              "path": cookie.path,
              "expiresAt": expiresMs,
              "secure": cookie.isSecure,
            ]
          }
          result(mapped)
        }
      case "setCookies":
        guard let list = call.arguments as? [[String: Any]] else {
          result(nil)
          return
        }
        let store = WKWebsiteDataStore.default().httpCookieStore
        let group = DispatchGroup()
        for item in list {
          guard
            let domain = item["domain"] as? String,
            let name = item["name"] as? String,
            let value = item["value"] as? String
          else { continue }
          let path = (item["path"] as? String)?.isEmpty == false ? (item["path"] as! String) : "/"
          let secure = (item["secure"] as? Bool) ?? false
          var props: [HTTPCookiePropertyKey: Any] = [
            .domain: domain,
            .name: name,
            .value: value,
            .path: path,
          ]
          if secure {
            props[.secure] = "TRUE"
          }
          if let expiresMs = item["expiresAt"] as? NSNumber, expiresMs.int64Value > 0 {
            props[.expires] = Date(timeIntervalSince1970: Double(expiresMs.int64Value) / 1000.0)
          }
          if let cookie = HTTPCookie(properties: props) {
            group.enter()
            store.setCookie(cookie) {
              group.leave()
            }
          }
        }
        group.notify(queue: .main) {
          result(nil)
        }
      case "clearAllCookies":
        let store = WKWebsiteDataStore.default().httpCookieStore
        store.getAllCookies { cookies in
          let group = DispatchGroup()
          for cookie in cookies {
            group.enter()
            store.delete(cookie) {
              group.leave()
            }
          }
          group.notify(queue: .main) {
            result(nil)
          }
        }
      default:
        result(FlutterMethodNotImplemented)
      }
    }

    // 2. 轻量原生定位桥接：按需单次获取 WGS-84 坐标（供天气与 U净 定位纠偏）。
    let bridge = CampusLocationBridge()
    self.locationBridge = bridge
    let locationChannel = FlutterMethodChannel(
      name: "com.ecut.campuspocket/location",
      binaryMessenger: messenger
    )
    locationChannel.setMethodCallHandler { call, result in
      if call.method == "getCurrentLocation" {
        bridge.requestSingleLocation(result: result)
      } else {
        result(FlutterMethodNotImplemented)
      }
    }

    // 3. 原生 AVFoundation 离线二维码扫描器：零第三方 Pod 依赖。
    let qrChannel = FlutterMethodChannel(
      name: "com.ecut.campuspocket/qr_scanner",
      binaryMessenger: messenger
    )
    qrChannel.setMethodCallHandler { [weak self] call, result in
      guard call.method == "scanQr" else {
        result(FlutterMethodNotImplemented)
        return
      }
      let title = (call.arguments as? [String: Any])?["title"] as? String ?? "扫描二维码"
      DispatchQueue.main.async {
        self?.presentQrScanner(title: title, result: result)
      }
    }

    // 4. 原生 PHPicker + Vision 离线中文 OCR：零第三方依赖、零体积增长、不上传图片。
    let ocr = CampusOfflineOcrBridge()
    self.ocrBridge = ocr
    let ocrChannel = FlutterMethodChannel(
      name: "com.ecut.campuspocket/ocr",
      binaryMessenger: messenger
    )
    ocrChannel.setMethodCallHandler { call, result in
      guard call.method == "pickImageAndRecognizeText" else {
        result(FlutterMethodNotImplemented)
        return
      }
      DispatchQueue.main.async {
        guard let presenter = Self.topViewController() else {
          result(nil)
          return
        }
        ocr.pickAndRecognize(from: presenter, result: result)
      }
    }
  }

  private func presentQrScanner(title: String, result: @escaping FlutterResult) {
    guard let presenter = Self.topViewController() else {
      result(nil)
      return
    }
    let scanner = CampusQrScannerController(hintTitle: title) { [weak self] scanned in
      self?.activeScanner = nil
      result(scanned)
    }
    scanner.modalPresentationStyle = .fullScreen
    self.activeScanner = scanner
    presenter.present(scanner, animated: true)
  }

  private static func topViewController() -> UIViewController? {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let keyWindow = scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow })
      ?? scenes.flatMap { $0.windows }.first
    var top = keyWindow?.rootViewController
    while let presented = top?.presentedViewController {
      top = presented
    }
    return top
  }
}

private final class CampusLocationBridge: NSObject, CLLocationManagerDelegate {
  private let manager = CLLocationManager()
  private var pendingResult: FlutterResult?
  private var timeoutWorkItem: DispatchWorkItem?

  override init() {
    super.init()
    manager.delegate = self
    manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
  }

  func requestSingleLocation(result: @escaping FlutterResult) {
    finish(with: nil)
    pendingResult = result

    let status = manager.authorizationStatus
    switch status {
    case .denied, .restricted:
      finish(with: nil)
      return
    case .notDetermined:
      manager.requestWhenInUseAuthorization()
    case .authorizedWhenInUse, .authorizedAlways:
      manager.requestLocation()
    @unknown default:
      finish(with: nil)
      return
    }

    let timeout = DispatchWorkItem { [weak self] in
      self?.finish(with: nil)
    }
    timeoutWorkItem = timeout
    DispatchQueue.main.asyncAfter(deadline: .now() + 10.0, execute: timeout)
  }

  func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
    guard pendingResult != nil else { return }
    let status = manager.authorizationStatus
    if status == .authorizedWhenInUse || status == .authorizedAlways {
      manager.requestLocation()
    } else if status == .denied || status == .restricted {
      finish(with: nil)
    }
  }

  func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
    guard let loc = locations.last else {
      finish(with: nil)
      return
    }
    finish(with: [
      "latitude": loc.coordinate.latitude,
      "longitude": loc.coordinate.longitude,
    ])
  }

  func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
    finish(with: nil)
  }

  private func finish(with value: Any?) {
    timeoutWorkItem?.cancel()
    timeoutWorkItem = nil
    if let callback = pendingResult {
      pendingResult = nil
      callback(value)
    }
  }
}

private final class CampusQrScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
  private let hintTitle: String
  private let onFinish: (String?) -> Void
  private var session: AVCaptureSession?
  private var previewLayer: AVCaptureVideoPreviewLayer?
  private var completed = false

  init(hintTitle: String, onFinish: @escaping (String?) -> Void) {
    self.hintTitle = hintTitle
    self.onFinish = onFinish
    super.init(nibName: nil, bundle: nil)
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .black
    setupHeader()
    requestCameraAndStart()
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    previewLayer?.frame = view.bounds
  }

  private func setupHeader() {
    let closeBtn = UIButton(type: .system)
    closeBtn.setTitle("取消", for: .normal)
    closeBtn.setTitleColor(.white, for: .normal)
    closeBtn.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
    closeBtn.backgroundColor = UIColor.black.withAlphaComponent(0.55)
    closeBtn.layer.cornerRadius = 18
    closeBtn.contentEdgeInsets = UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
    closeBtn.translatesAutoresizingMaskIntoConstraints = false
    closeBtn.addTarget(self, action: #selector(handleCancel), for: .touchUpInside)

    let label = UILabel()
    label.text = hintTitle
    label.textColor = .white
    label.font = .systemFont(ofSize: 16, weight: .semibold)
    label.textAlignment = .center
    label.translatesAutoresizingMaskIntoConstraints = false

    view.addSubview(closeBtn)
    view.addSubview(label)
    NSLayoutConstraint.activate([
      closeBtn.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
      closeBtn.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      label.centerYAnchor.constraint(equalTo: closeBtn.centerYAnchor),
      label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
    ])
  }

  private func requestCameraAndStart() {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
      startCapture()
    case .notDetermined:
      AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
        DispatchQueue.main.async {
          if granted {
            self?.startCapture()
          } else {
            self?.finish(with: nil)
          }
        }
      }
    default:
      finish(with: nil)
    }
  }

  private func startCapture() {
    let captureSession = AVCaptureSession()
    guard
      let device = AVCaptureDevice.default(for: .video),
      let input = try? AVCaptureDeviceInput(device: device),
      captureSession.canAddInput(input)
    else {
      finish(with: nil)
      return
    }
    captureSession.addInput(input)

    let output = AVCaptureMetadataOutput()
    guard captureSession.canAddOutput(output) else {
      finish(with: nil)
      return
    }
    captureSession.addOutput(output)
    output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
    output.metadataObjectTypes = [.qr]

    let layer = AVCaptureVideoPreviewLayer(session: captureSession)
    layer.videoGravity = .resizeAspectFill
    layer.frame = view.bounds
    view.layer.insertSublayer(layer, at: 0)

    self.session = captureSession
    self.previewLayer = layer
    DispatchQueue.global(qos: .userInitiated).async {
      captureSession.startRunning()
    }
  }

  func metadataOutput(
    _ output: AVCaptureMetadataOutput,
    didOutput metadataObjects: [AVMetadataObject],
    from connection: AVCaptureConnection
  ) {
    guard
      let readable = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
      let value = readable.stringValue,
      !value.isEmpty
    else { return }
    finish(with: value)
  }

  @objc private func handleCancel() {
    finish(with: nil)
  }

  private func finish(with value: String?) {
    guard !completed else { return }
    completed = true
    session?.stopRunning()
    dismiss(animated: true) { [onFinish] in
      onFinish(value)
    }
  }
}

private final class CampusOfflineOcrBridge: NSObject, PHPickerViewControllerDelegate {
  private var pendingResult: FlutterResult?

  func pickAndRecognize(from presenter: UIViewController, result: @escaping FlutterResult) {
    finish(with: nil)
    pendingResult = result
    var config = PHPickerConfiguration()
    config.selectionLimit = 1
    config.filter = .images
    let picker = PHPickerViewController(configuration: config)
    picker.delegate = self
    presenter.present(picker, animated: true)
  }

  func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
    picker.dismiss(animated: true)
    guard let provider = results.first?.itemProvider, provider.canLoadObject(ofClass: UIImage.self) else {
      finish(with: nil)
      return
    }
    provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
      guard let image = object as? UIImage, let cgImage = image.cgImage else {
        DispatchQueue.main.async {
          self?.finish(with: "")
        }
        return
      }
      self?.recognizeText(in: cgImage)
    }
  }

  private func recognizeText(in cgImage: CGImage) {
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      let request = VNRecognizeTextRequest { [weak self] req, err in
        if err != nil {
          DispatchQueue.main.async { self?.finish(with: "") }
          return
        }
        let observations = (req.results as? [VNRecognizedTextObservation]) ?? []
        struct LineItem {
          let topY: CGFloat
          let leftX: CGFloat
          let height: CGFloat
          let text: String
        }
        let items: [LineItem] = observations.compactMap { obs in
          guard let candidate = obs.topCandidates(1).first else { return nil }
          let text = candidate.string.trimmingCharacters(in: .whitespacesAndNewlines)
          guard !text.isEmpty else { return nil }
          // Vision 原点在左下角，转换为自上而下阅读顺序
          let topY = 1.0 - obs.boundingBox.midY
          return LineItem(
            topY: topY,
            leftX: obs.boundingBox.minX,
            height: max(obs.boundingBox.height, 0.015),
            text: text
          )
        }.sorted { a, b in
          if abs(a.topY - b.topY) <= max(a.height, b.height) * 0.55 {
            return a.leftX < b.leftX
          }
          return a.topY < b.topY
        }

        var rows: [[LineItem]] = []
        for item in items {
          if let lastRow = rows.last,
             let ref = lastRow.first,
             abs(item.topY - ref.topY) <= max(item.height, ref.height) * 0.6 {
            rows[rows.count - 1].append(item)
          } else {
            rows.append([item])
          }
        }
        let merged = rows.map { row in
          row.sorted { $0.leftX < $1.leftX }.map { $0.text }.joined(separator: " ")
        }.joined(separator: "\n")

        DispatchQueue.main.async {
          self?.finish(with: merged)
        }
      }
      request.recognitionLevel = .accurate
      request.usesLanguageCorrection = true
      request.recognitionLanguages = ["zh-Hans", "en-US"]

      let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
      do {
        try handler.perform([request])
      } catch {
        DispatchQueue.main.async {
          self?.finish(with: "")
        }
      }
    }
  }

  private func finish(with value: String?) {
    if let callback = pendingResult {
      pendingResult = nil
      callback(value)
    }
  }
}

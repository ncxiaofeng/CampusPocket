import CoreLocation
import Flutter
import UIKit
import WebKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  private var locationBridge: CampusLocationBridge?

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)

    let messenger = engineBridge.applicationRegistrar.messenger()

    // WKHTTPCookieStore 原生桥接：支持读取/注入包含 HttpOnly 在内的完整 Cookie（含真实 domain/path），
    // 解决 WKWebView 与 Dart HttpClient 之间 CAS CASTGC、正方 /jwglxt 与 /sso 双 JSESSIONID 同步问题。
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

    // 轻量原生定位桥接：仅按需请求一次当前位置，供首页 Open-Meteo 天气使用。
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

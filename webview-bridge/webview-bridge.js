/**
 * Iman Sangi webview bridge SDK.
 * Include this file in the web app and use window.NoorBridge from React.
 *
 * Every call resolves with the native data object, or rejects with an Error that
 * carries .permissionRequired when Android needs a permission first.
 */
(function (global) {
  "use strict";

  var pending = new Map();
  var listeners = new Map();
  var counter = 0;

  function nativeAvailable() {
    return typeof window !== "undefined" && !!window.AndroidBridge;
  }

  function call(method, params) {
    return new Promise(function (resolve, reject) {
      if (!nativeAvailable()) {
        reject(new Error("Native bridge is not available (browser mode)"));
        return;
      }
      var requestId = "r" + Date.now() + "_" + ++counter;
      var timer = setTimeout(function () {
        if (pending.has(requestId)) {
          pending.delete(requestId);
          reject(new Error("Native call timed out: " + method));
        }
      }, 20000);

      pending.set(requestId, {
        resolve: function (value) {
          clearTimeout(timer);
          resolve(value);
        },
        reject: function (error) {
          clearTimeout(timer);
          reject(error);
        }
      });

      try {
        window.AndroidBridge.call(method, JSON.stringify(params || {}), requestId);
      } catch (error) {
        clearTimeout(timer);
        pending.delete(requestId);
        reject(error);
      }
    });
  }

  function emit(event, payload) {
    var handlers = listeners.get(event);
    if (!handlers) return;
    handlers.slice().forEach(function (handler) {
      try {
        handler(payload);
      } catch (error) {
        console.error("bridge listener failed", error);
      }
    });
  }

  window.NoorNative = {
    _onBridge: function (raw) {
      var envelope;
      try {
        envelope = JSON.parse(raw);
      } catch (error) {
        return;
      }
      var entry = pending.get(envelope.requestId);
      if (!entry) return;
      pending.delete(envelope.requestId);
      if (envelope.success) {
        entry.resolve(envelope.data);
      } else {
        var failure = new Error(envelope.error || "Native call failed");
        failure.permissionRequired = envelope.permissionRequired || null;
        entry.reject(failure);
      }
    },
    _onEvent: function (raw) {
      var envelope;
      try {
        envelope = JSON.parse(raw);
      } catch (error) {
        return;
      }
      emit(envelope.type, envelope.payload);
    }
  };

  var NoorBridge = {
    isAvailable: nativeAvailable,

    on: function (event, handler) {
      var handlers = listeners.get(event) || [];
      handlers.push(handler);
      listeners.set(event, handlers);
      return function () {
        var current = listeners.get(event) || [];
        listeners.set(
          event,
          current.filter(function (item) {
            return item !== handler;
          })
        );
      };
    },

    device: {
      getInfo: function () {
        return call("getDeviceInfo");
      },
      getAppList: function () {
        return call("getAppList");
      },
      getPermissionsStatus: function () {
        return call("getPermissionsStatus");
      }
    },

    lock: {
      selectApps: function (packages) {
        return call("selectBlockedApps", { packages: packages });
      },
      getBlockedApps: function () {
        return call("getBlockedApps");
      },
      start: function (requirementMinutes, continuous) {
        return call("startAppBlocking", {
          requirementMinutes: requirementMinutes,
          continuous: !!continuous
        });
      },
      stop: function (emergency) {
        return call("stopAppBlocking", { emergency: !!emergency });
      },
      status: function () {
        return call("getBlockingStatus");
      },
      usage: function () {
        return call("getUsageStats");
      }
    },

    quran: {
      play: function (options) {
        return call("startQuranPlayback", options || {});
      },
      pause: function () {
        return call("pauseQuranPlayback");
      },
      resume: function () {
        return call("resumeQuranPlayback");
      },
      stop: function () {
        return call("stopQuranPlayback");
      },
      seek: function (positionMs) {
        return call("seekQuranPlayback", { positionMs: positionMs });
      },
      setSpeed: function (speed) {
        return call("setPlaybackSpeed", { speed: speed });
      },
      status: function () {
        return call("getPlaybackStatus");
      },
      listening: function () {
        return call("getListeningTime");
      },
      setRequirement: function (requirementMinutes, continuous) {
        return call("setListeningRequirement", {
          requirementMinutes: requirementMinutes,
          continuous: !!continuous
        });
      }
    },

    focus: {
      start: function (minutes) {
        return call("startFocusTimer", { minutes: minutes });
      },
      pause: function () {
        return call("pauseFocusTimer");
      },
      resume: function () {
        return call("resumeFocusTimer");
      },
      status: function () {
        return call("getFocusTimerStatus");
      },
      history: function () {
        return call("getFocusHistory");
      }
    },

    prayer: {
      times: function (options) {
        return call("getPrayerTimes", options || {});
      },
      scheduleAdhan: function (options) {
        return call("scheduleAdhanAlarm", options || {});
      },
      cancelAdhan: function (prayer) {
        return call("cancelAdhanAlarm", { prayer: prayer });
      },
      qibla: function (latitude, longitude) {
        return call("getQiblaDirection", { latitude: latitude, longitude: longitude });
      },
      startCompass: function () {
        return call("startCompass");
      },
      stopCompass: function () {
        return call("stopCompass");
      }
    },

    sound: {
      setRingtone: function (soundUri) {
        return call("setRingtone", { soundUri: soundUri });
      },
      preview: function (soundUri) {
        return call("previewSound", { soundUri: soundUri });
      },
      setVibration: function (enabled) {
        return call("setVibration", { enabled: !!enabled });
      },
      notify: function (title, body) {
        return call("showNotification", { title: title, body: body });
      }
    },

    protection: {
      enable: function () {
        return call("enableAdultProtection");
      },
      disable: function () {
        return call("disableAdultProtection");
      },
      status: function () {
        return call("getProtectionStatus");
      },
      openPrivateDns: function () {
        return call("openPrivateDnsSettings");
      }
    },

    storage: {
      save: function (key, value) {
        return call("saveLocalData", { key: key, value: String(value) });
      },
      get: function (key) {
        return call("getLocalData", { key: key });
      },
      remove: function (key) {
        return call("deleteLocalData", { key: key });
      }
    },

    system: {
      requestPermission: function (permission) {
        return call("requestPermission", { permission: permission });
      },
      openSettings: function (target) {
        return call("openSystemSettings", { target: target });
      },
      share: function (text, url) {
        return call("shareContent", { text: text, url: url || "" });
      },
      version: function () {
        return call("getAppVersion");
      }
    }
  };

  global.NoorBridge = NoorBridge;
})(typeof window !== "undefined" ? window : globalThis);

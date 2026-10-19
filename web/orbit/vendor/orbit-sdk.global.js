"use strict";
var Orbit = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // src/index.ts
  var index_exports = {};
  __export(index_exports, {
    SdkError: () => SdkError,
    createGameSdk: () => createGameSdk
  });

  // src/protocol.ts
  var PROTOCOL = 1;
  var SdkError = class extends Error {
    constructor(code, message) {
      super(message);
      this.code = code;
      this.name = "SdkError";
    }
    code;
  };

  // src/client.ts
  function connectPort(port, timeout = 1e4) {
    let closed = false;
    const pending = /* @__PURE__ */ new Map();
    port.onmessage = ({ data }) => {
      if (closed || !data || data.v !== PROTOCOL || typeof data.id !== "string")
        return;
      const p = pending.get(data.id);
      if (!p) return;
      pending.delete(data.id);
      clearTimeout(p.timer);
      if (data.error && typeof data.error.code === "string")
        p.reject(
          new SdkError(
            data.error.code,
            String(data.error.message || "B\u0142\u0105d operacji")
          )
        );
      else if (Object.hasOwn(data, "result")) p.resolve(data.result);
      else
        p.reject(
          new SdkError("INVALID_RESPONSE", "Nieprawid\u0142owa odpowied\u017A launchera")
        );
    };
    port.start();
    return {
      call(method, params) {
        if (closed)
          return Promise.reject(new SdkError("CLOSED", "Kana\u0142 zosta\u0142 zamkni\u0119ty"));
        const id = crypto.randomUUID();
        return new Promise((resolve, reject) => {
          const timer = setTimeout(() => {
            pending.delete(id);
            reject(new SdkError("TIMEOUT", "Launcher nie odpowiedzia\u0142"));
          }, timeout);
          pending.set(id, { resolve, reject, timer });
          try {
            port.postMessage({ v: PROTOCOL, id, method, params });
          } catch {
            pending.delete(id);
            clearTimeout(timer);
            reject(new SdkError("INVALID_REQUEST", "Nie mo\u017Cna wys\u0142a\u0107 \u017C\u0105dania"));
          }
        });
      },
      dispose() {
        closed = true;
        port.onmessage = null;
        port.close();
        for (const p of pending.values()) {
          clearTimeout(p.timer);
          p.reject(new SdkError("CLOSED", "Kana\u0142 zosta\u0142 zamkni\u0119ty"));
        }
        pending.clear();
      }
    };
  }

  // src/index.ts
  function createGameSdk({
    launcherOrigins
  }) {
    let client;
    let closed = false;
    const nonce = crypto.randomUUID();
    let resolveReady;
    let rejectReady;
    const ready = new Promise((resolve, reject) => {
      resolveReady = resolve;
      rejectReady = reject;
    });
    void ready.catch(() => {
    });
    let timer;
    let deadline;
    const cleanup = () => {
      clearInterval(timer);
      clearTimeout(deadline);
      window.removeEventListener("message", listener);
    };
    const listener = (event) => {
      if (closed || event.source !== window.parent || !launcherOrigins.includes(event.origin) || event.data?.type !== "orbit:connect" || event.data?.v !== 1 || event.ports.length !== 1)
        return;
      client = connectPort(event.ports[0]);
      cleanup();
      resolveReady();
    };
    window.addEventListener("message", listener);
    let targetOrigin = "*";
    try {
      const referrerOrigin = new URL(document.referrer).origin;
      if (launcherOrigins.includes(referrerOrigin) && /^https?:/.test(referrerOrigin))
        targetOrigin = referrerOrigin;
    } catch {
    }
    const announce = () => window.parent.postMessage(
      { type: "orbit:ready", v: 1, nonce },
      targetOrigin
    );
    timer = setInterval(announce, 250);
    deadline = setTimeout(() => {
      cleanup();
      rejectReady(new SdkError("TIMEOUT", "Uruchom gr\u0119 z launchera ORBIT"));
    }, 1e4);
    announce();
    const call = async (method, params) => {
      await ready;
      if (closed) throw new SdkError("CLOSED", "SDK zamkni\u0119te");
      return client.call(method, params);
    };
    return {
      ready: () => ready,
      player: { get: () => call("player.get", {}) },
      saves: {
        get: (slot) => call("saves.get", { slot }),
        put: (slot, data, { expectedRevision }) => call("saves.put", { slot, data, expectedRevision })
      },
      assets: {
        list: ({ cursor } = {}) => call("assets.list", cursor ? { cursor } : {}),
        getUrl: (assetId) => call("assets.getUrl", { assetId })
      },
      multiplayer: {
        list: () => call("multiplayer.list", {}),
        create: (options) => call("multiplayer.create", { ...options }),
        // Pass this short-lived room capability to Colyseus joinById(externalRoomId, {ticket}).
        // The launcher session is never exposed to the game. Endpoint must be a trusted provider.
        join: (roomId, { inviteCode } = {}) => call("multiplayer.join", {
          roomId,
          ...inviteCode === void 0 ? {} : { inviteCode }
        }),
        close: (roomId) => call("multiplayer.close", { roomId })
      },
      leaderboard: {
        list: (mode = "tic-tac-toe") => call("leaderboard.list", { mode })
      },
      achievements: {
        unlock: (key) => call("achievements.unlock", { key })
      },
      dispose() {
        closed = true;
        cleanup();
        client?.dispose();
        rejectReady(new SdkError("CLOSED", "SDK zamkni\u0119te"));
      }
    };
  }
  return __toCommonJS(index_exports);
})();

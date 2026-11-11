#!/usr/bin/env python3
"""验证用户创建接口；仅使用 Python 标准库，不修改服务配置。

python3 scripts/check_create_user.py --check-only
python3 scripts/check_create_user.py --login-only
python3 scripts/check_create_user.py

默认流程：获取 CSRF → 使用 Spring 开发账号登录 → 创建测试用户 → 重复提交。
本机默认测试账号为 user / XiaoMing@123，无需输入密码。
可用 USER_SERVICE_TEST_PASSWORD 环境变量覆盖，或用 --ask-password 手动输入。
成功时会留下一名测试用户，不自动删除。
"""

import argparse
import getpass
import http.cookiejar
import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from html.parser import HTMLParser


class LoginForm(HTMLParser):
    def __init__(self):
        super().__init__()
        self.csrf = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and attrs.get("name") == "_csrf":
            self.csrf = attrs.get("value")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # 保留原始状态码，避免把跳转后的登录页面误判为接口成功。
        return None


def csrf_from(body):
    parser = LoginForm()
    parser.feed(body)
    return parser.csrf


def redact(value):
    if isinstance(value, dict):
        return {
            key: "[已隐藏]" if any(word in key.lower() for word in ("password", "token", "secret"))
            else redact(item)
            for key, item in value.items()
        }
    if isinstance(value, list):
        return [redact(item) for item in value]
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:3001")
    parser.add_argument("--username", default="user", help="Spring 登录用户名，不是数据库用户名")
    parser.add_argument("--mobile", help="测试手机号；默认随机生成一个 11 位号码")
    parser.add_argument("--check-only", action="store_true", help="只检查连接及登录页，不登录、不创建用户")
    parser.add_argument("--login-only", action="store_true", help="验证登录和 CSRF，不创建用户")
    parser.add_argument("--ask-password", action="store_true", help="手动输入登录密码，覆盖默认值和环境变量")
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    parts = urllib.parse.urlsplit(base)
    if parts.scheme not in ("http", "https") or not parts.netloc or parts.username or parts.password:
        parser.error("base-url 必须是无内嵌账号密码的 HTTP(S) 地址")
    if parts.scheme == "http" and parts.hostname not in ("localhost", "127.0.0.1", "::1"):
        parser.error("非本机测试请使用 HTTPS，避免明文传输登录密码")

    cookies = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}),
        urllib.request.HTTPCookieProcessor(cookies),
        NoRedirect(),
    )

    def request(path, payload=None, headers=None):
        req = urllib.request.Request(base + path, data=payload, headers=headers or {})
        try:
            response = opener.open(req, timeout=15)
        except urllib.error.HTTPError as exc:
            response = exc
        with response:
            return response.code, response.headers, response.read().decode("utf-8", errors="replace")

    print("目标接口：" + base + "/user/create/user")
    status, _, body = request("/login")
    csrf = csrf_from(body)
    print(f"登录页：HTTP {status}；CSRF 字段：{'找到' if csrf else '未找到'}")
    if status != 200 or not csrf:
        print("未识别到 Spring 默认登录表单。请确认运行的是 user-service，以及是否更改了安全配置。")
        return 1
    if args.check_only:
        print("连接检查通过。本次没有登录或发送创建用户请求。")
        return 0

    password = os.environ.get("USER_SERVICE_TEST_PASSWORD")
    if args.ask_password:
        password = getpass.getpass("请输入 Spring 登录账号的原始密码（输入不显示）：")
    elif password is None:
        if parts.hostname not in ("localhost", "127.0.0.1", "::1"):
            parser.error("远程测试请通过 USER_SERVICE_TEST_PASSWORD 或 --ask-password 指定密码")
        password = "XiaoMing@123"
        print("使用本机默认开发测试账号自动登录，无需输入密码。")
    login = urllib.parse.urlencode({"username": args.username, "password": password, "_csrf": csrf}).encode()
    status, headers, _ = request("/login", login, {"Content-Type": "application/x-www-form-urlencoded"})
    location = headers.get("Location", "")
    login_failed = "error" in urllib.parse.parse_qs(urllib.parse.urlsplit(location).query, keep_blank_values=True)
    if status not in (302, 303) or login_failed:
        print(f"登录失败：HTTP {status}；跳转路径：{urllib.parse.urlsplit(location).path or '无'}" + ("?error" if login_failed else ""))
        print("302 本身只是跳转；跳转到 /login?error 才表示认证失败。")
        print("检查账号密码以及 PasswordEncoder：自定义 BCrypt 编码器后，默认开发账号保存的密码也必须是 BCrypt 哈希。")
        print("如果已配置 spring.security.user.password 为哈希，这里应输入对应的原始密码。")
        return 1

    # 登录成功可能轮换会话和清除旧 CSRF Token，重新读取当前会话的令牌。
    status, _, body = request("/login")
    csrf = csrf_from(body)
    if status != 200 or not csrf:
        print("登录后无法获取新的 CSRF Token；停止测试，避免把安全拦截误判成业务错误。")
        return 1

    print("登录通过，已获取当前会话的 CSRF Token。")
    if args.login_only:
        print("本次仅验证登录，没有发送创建用户请求。")
        return 0

    mobile = args.mobile or "199" + f"{secrets.randbelow(100_000_000):08d}"
    print("测试手机号：" + mobile + "；成功后会保留一条测试用户记录。")

    payload = json.dumps({
        "mobile": mobile,
        "password": "TestUser@123",
        "nickname": "接口验证用户",
    }, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json", "X-CSRF-TOKEN": csrf}

    def post(label):
        status, _, body = request("/user/create/user", payload, headers)
        print(f"\n{label}：HTTP {status}")
        try:
            result = json.loads(body)
        except ValueError:
            result = None
        if result is not None:
            print(json.dumps(redact(result), ensure_ascii=False, indent=2))
        else:
            print("响应不是 JSON（可能是登录或错误页面），未输出 HTML 原文。")
        return status, result

    status, result = post("1. 创建用户，期望 201")
    if status not in (200, 201):
        print({
            400: "检查请求校验错误、JSON 字段或字段类型。",
            401: "登录身份无效，检查当前安全配置。",
            403: "请求可能被 CSRF 或权限规则拦截，尚不能据此判断注册逻辑。",
            404: "检查 Controller 路径、应用是否重启，以及服务端日志。",
            409: "手机号可能已存在；当前代码也会把其他插入异常误报成 409。",
            500: "查看 IDEA 日志；当前源码缺少 @RequestBody，JSON 可能未绑定，导致 getMobile().trim() 空指针。",
            302: "请求被重定向，可能仍未通过登录验证。",
        }.get(status, "请结合 IDEA 日志检查响应原因。"))
        return 1

    failed = status != 201
    if status == 200:
        print("提示：接口返回 200；创建成功应按约定返回 201。")
    data = result.get("data") if isinstance(result, dict) else None
    if not isinstance(data, dict) or not (data.get("userId") or data.get("id")):
        print("未获得创建用户的 ID，不能把响应当作已成功保存。")
        return 1
    if "passwordHash" in data or "password_hash" in data or "password" in data:
        print("问题：响应暴露密码相关字段；应返回 UserResponse，不能直接返回 UserAccount。")
        failed = True
    if "userId" not in data:
        print("问题：响应字段应使用 userId，与项目约定一致。")
        failed = True

    status, _ = post("2. 相同手机号重复提交，期望 409")
    if status != 409:
        print("重复手机号测试未通过。")
        failed = True
    print("\n验证结束。" + ("存在需要修正的问题。" if failed else "上述两项检查通过。"))
    print("数据库检查：SELECT id, mobile, nickname, status FROM byte_mall.user_account WHERE mobile = '" + mobile.replace("'", "''") + "';")
    return 1 if failed else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (urllib.error.URLError, TimeoutError) as exc:
        print("连接失败：", exc)
        sys.exit(1)
    except (EOFError, KeyboardInterrupt):
        print("\n已停止。请在交互式终端中输入登录密码。")
        sys.exit(1)

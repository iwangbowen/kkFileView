# E2E 完善清单（基于 PR342 回归经验）

## 背景
本次手工回归已经验证了以下关键链路：
- TXT
- XLSX
- ZIP
- PDF
- DOCX
- MP4
- CAD / DXF

但当前 GitHub CI 中，自动化 E2E 仅覆盖了其中一部分，且大多只断言 HTTP 200，没有校验最终预览效果。

## 本次落地目标

### 1. 补齐缺失的关键链路
- [x] PDF 预览 smoke
- [x] MP4 预览 smoke
- [x] CAD / DXF 预览 smoke

### 2. 升级断言方式
- [x] 不再只看 `status === 200`
- [x] 增加标题/页面关键字断言，确认命中了正确预览模板
- [x] 对 PDF / DOCX / CAD 增加“等待页 -> 最终页面”的轮询兼容

### 3. 补齐 CI 所需 fixture
- [x] `sample.pdf` 进入 required fixture 清单
- [x] `sample.mp4` 进入 required fixture 清单
- [x] `text.dxf` 进入 required fixture 清单
- [x] 将 MP4 与 DXF fixture 作为仓库内静态样例纳入 CI

### 4. 后续可继续增强（本次未全部落地）
- [ ] 为 PDF / Office / CAD 增加截图型 nightly artifact
- [ ] 为 `/picturesPreview` 增加独立 smoke
- [ ] 为 OFD 增加稳定 fixture 和 smoke case
- [ ] 为媒体预览增加更多格式（如 wav / mp3 / mov）
- [ ] 为 CAD 增加第二份标准样例，避免单样例偏差
- [ ] 将当前 HTML 测试报告模板收敛成 nightly 自动产物

## 预期收益
- 让 CI 覆盖这次 PR342 真正验证过的关键主链路
- 避免未来出现“CI 绿了，但 PDF / MP4 / CAD 实际挂了”的情况
- 让 E2E 更接近用户真实感知，而不是仅验证接口可达

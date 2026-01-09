# Docker 打包、构建与发布指南

本文档描述了如何打包、构建和发布 kkFileView 的 Docker 镜像。

## 前置条件

- Maven 3.6+
- Docker 20.10+
- 已构建好 `kkfileview-base:latest` 镜像

## 步骤一：打包项目

进入 server 目录，执行 Maven 打包命令：

```bash
cd server
mvn clean package -DskipTests
cd ..
```

该命令会：
- 清理之前的构建文件
- 编译 Java 源代码
- 打包为可执行的 tar.gz 文件
- 输出到 `server/target/` 目录

## 步骤二：构建 Docker 镜像

在项目根目录执行以下命令构建镜像：

```bash
docker build -t kkfileview:4.4.3 .
```

该命令会：
- 基于 `kkfileview-base:latest` 镜像构建
- 将打包后的应用放入容器
- 生成标签为 `kkfileview:4.4.3` 的镜像

## 步骤三：发布镜像到 Docker Hub

完成镜像构建后，执行以下命令发布到 Docker Hub：

```bash
# 标记镜像，使用用户命名空间
docker tag kkfileview:4.4.3 wangbowen/kkfileview:4.4.3
docker tag kkfileview:4.4.3 wangbowen/kkfileview:latest

# 推送镜像到 Docker Hub
docker push wangbowen/kkfileview:4.4.3
docker push wangbowen/kkfileview:latest
```

## 完整一键构建脚本

如果需要一次性执行所有步骤，可以使用以下脚本：

```bash
#!/bin/bash

# 版本号
VERSION="4.4.3"
NAMESPACE="wangbowen"

echo "========== 开始打包 =========="
cd server
mvn clean package -DskipTests || exit 1
cd ..

echo "========== 开始构建镜像 =========="
docker build -t kkfileview:${VERSION} . || exit 1

echo "========== 开始标记镜像 =========="
docker tag kkfileview:${VERSION} ${NAMESPACE}/kkfileview:${VERSION}
docker tag kkfileview:${VERSION} ${NAMESPACE}/kkfileview:latest

echo "========== 开始推送镜像 =========="
docker push ${NAMESPACE}/kkfileview:${VERSION} || exit 1
docker push ${NAMESPACE}/kkfileview:latest || exit 1

echo "========== 构建完成 =========="
echo "镜像已推送到: ${NAMESPACE}/kkfileview:${VERSION} 和 ${NAMESPACE}/kkfileview:latest"
```

## 验证镜像

构建完成后，可以验证镜像是否成功创建：

```bash
# 查看本地镜像
docker images | grep kkfileview

# 运行镜像进行测试
docker run -p 8012:8012 kkfileview:4.4.3
```

## 注意事项

1. **版本号更新**：每次发布新版本时，请更新 Dockerfile 中的版本号和 jar 包名称
2. **基础镜像**：确保 `kkfileview-base:latest` 镜像已经存在
3. **Docker Hub 认证**：推送前需要执行 `docker login` 登录 Docker Hub
4. **磁盘空间**：构建过程需要足够的磁盘空间，特别是打包阶段

## 常见问题

### Q: 如何修改镜像版本号？

A: 修改上述命令中的 `VERSION` 变量即可，例如改为 `4.4.4`

### Q: 如何使用私有 Docker 仓库？

A: 将 `wangbowen/kkfileview` 替换为你的私有仓库地址，例如 `registry.example.com/kkfileview`

### Q: 如何查看构建日志？

A: 构建过程中的所有日志都会输出到控制台，也可以使用 `docker logs` 查看容器运行日志

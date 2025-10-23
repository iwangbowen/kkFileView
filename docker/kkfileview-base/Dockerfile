FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV LANG=zh_CN.UTF-8
ENV LC_ALL=zh_CN.UTF-8

# 清理旧的 apt 源并使用清华镜像
RUN rm -rf /etc/apt/sources.list.d/* && \
    echo "deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ noble main restricted universe multiverse\n\
    deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ noble-updates main restricted universe multiverse\n\
    deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ noble-backports main restricted universe multiverse\n\
    deb http://mirrors.tuna.tsinghua.edu.cn/ubuntu/ noble-security main restricted universe multiverse" > /etc/apt/sources.list && \
    apt-get clean && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-21-jre \
    tzdata locales libc-bin xfonts-utils fontconfig libxslt1.1 \
    cabextract wget && \
    echo "Asia/Shanghai" > /etc/timezone && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    localedef -i zh_CN -c -f UTF-8 -A /usr/share/locale/locale.alias zh_CN.UTF-8 && \
    locale-gen zh_CN.UTF-8

# 拷贝并安装 LibreOffice
COPY libreoffice/LibreOffice_25.8.2_Linux_x86-64_deb.tar.gz /tmp/

RUN tar zxvf /tmp/LibreOffice_25.8.2_Linux_x86-64_deb.tar.gz -C /tmp && \
    cd /tmp/LibreOffice_25.8.2.2_Linux_x86-64_deb/DEBS/ && \
    dpkg -i *.deb || true && \
    apt-get install -f -y

# 安装字体包
RUN echo "ttf-mscorefonts-installer msttcorefonts/accepted-mscorefonts-eula select true" | debconf-set-selections && \
    echo "media-types media-types/install-mime.types select install" | debconf-set-selections && \
    apt-get install -y --no-install-recommends \
    -o Dpkg::Options::="--force-confdef" \
    -o Dpkg::Options::="--force-confold" \
    ttf-mscorefonts-installer \
    ttf-wqy-microhei \
    ttf-wqy-zenhei \
    xfonts-wqy && \
    apt-get autoremove -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /tmp/LibreOffice_25.8.2.2_Linux_x86-64_deb /tmp/LibreOffice_25.8.2_Linux_x86-64_deb.tar.gz

# 拷贝中文字体并刷新缓存
ADD fonts/* /usr/share/fonts/chinese/

RUN cd /usr/share/fonts/chinese && \
    mkfontscale && \
    mkfontdir && \
    fc-cache -fv

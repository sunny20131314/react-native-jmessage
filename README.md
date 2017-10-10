# React Native JMessage
[![NPM](https://nodei.co/npm/react-native-jmessage.png?downloads=true&stars=true)](https://nodei.co/npm/react-native-jmessage/)

极光IM React Native 模块

支持iOS和android，支持RN@0.40+

## 文档
- [中文文档](https://xsdlr.github.io/react-native-jmessage)
- [English Document](https://xsdlr.github.io/react-native-jmessage/#/en/)

## 构建
本模块使用官方jmessage版本如下：
- [x] iOS SDK 3.2.1 build 139
- [x] Android SDK 2.2.0

## 在原工程上的修改（由于自己的工程使用到了jpush-react-native）：
1、引用的jcore包为jcore-react-native工程，而在自己的工程中，需要在setting.gradle中设置jcore路径，删除build.gradle 中的compile project(':jpush-react-native')

## 功能
- [x] 发送消息(文本、图片)
- [x] 登录、注销账户
- [x] 获得用户信息
- [x] 获取历史消息
- [x] 获取会话列表

## 示例
[react-native-jmessage-example](https://github.com/xsdlr/react-native-jmessage-example)


package com.xsdlr.rnjmessage;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.graphics.Bitmap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.xsdlr.rnjmessage.model.ConversationIDJSONModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import cn.jiguang.api.JCoreInterface;
import cn.jpush.im.android.api.JMessageClient;
import cn.jpush.im.android.api.content.ImageContent;
import cn.jpush.im.android.api.content.MessageContent;
import cn.jpush.im.android.api.content.TextContent;
import cn.jpush.im.android.api.content.EventNotificationContent;
import cn.jpush.im.android.api.enums.ContentType;
import cn.jpush.im.android.api.enums.ConversationType;
import cn.jpush.im.android.api.event.MessageEvent;
import cn.jpush.im.android.api.event.OfflineMessageEvent;
import cn.jpush.im.android.api.event.NotificationClickEvent;
import cn.jpush.im.android.api.event.LoginStateChangeEvent;
import cn.jpush.im.android.api.model.Conversation;
import cn.jpush.im.android.api.model.GroupInfo;
import cn.jpush.im.android.api.model.Message;
import cn.jpush.im.android.api.model.UserInfo;
import cn.jpush.im.android.api.options.MessageSendingOptions;
import cn.jpush.im.api.BasicCallback;

/**
 * Created by xsdlr on 2016/12/14.
 */
public class JMessageModule extends ReactContextBaseJavaModule {

    static boolean isDebug;

    public JMessageModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "JMessageModule";
    }

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("AppKey", getMetaData("JPUSH_APPKEY"));
        constants.put("MasterSecret", getMetaData("JPUSH_MASTER_SECRET"));
        return constants;
    }
    /**
     * 初始化方法
     */
    @ReactMethod
    public void setupJMessage() {
        JMessageClient.init(this.getReactApplicationContext());
        JMessageClient.registerEventReceiver(this);
        JCoreInterface.setDebugMode(JMessageModule.isDebug);
    }
    /**
     * 获得注册id
     * @param promise
     */
    @ReactMethod
    public void getRegistrationID(final Promise promise) {
        Activity context = getCurrentActivity();
        if (context == null) {
            promise.reject(JMessageException.ACTIVITY_NOT_EXIST.getCode(), JMessageException.ACTIVITY_NOT_EXIST);
            return;
        }
        String id = JCoreInterface.getRegistrationID(context);
        promise.resolve(id);
    }
    /**
     * 用户是否登录
     * @param promise
     */
    @ReactMethod
    public void isLoggedIn(final Promise promise) {
        UserInfo info = JMessageClient.getMyInfo();
        System.out.println(info);
        boolean isLoggedIn = info != null && info.getUserID() != 0;
        promise.resolve(isLoggedIn);
    }
    /**
     * 登录
     * @param username  用户名
     * @param password  密码
     * @param promise
     */
    @ReactMethod
    public void login(String username, String password, final Promise promise) {
        final JMessageModule _this = this;
        JMessageClient.login(username, password, new BasicCallback() {
            @Override
            public void gotResult(int responseCode, String loginDesc) {
                if (responseCode == 0) {
                    _this.myInfo(promise);
                } else {
                    promise.reject(String.valueOf(responseCode), loginDesc);
                }
            }
        });
    }
    /**
     * 注销
     */
    @ReactMethod
    public void logout(final Promise promise) {
        JMessageClient.logout();
        promise.resolve(null);
    }
    /**
     * 获得用户信息
     * @param promise
     */
    @ReactMethod
    public void myInfo(final Promise promise) {
        UserInfo info = JMessageClient.getMyInfo();
        if (info == null) {
            promise.reject(null, "获取用户信息失败");
            return;
        }
        WritableMap result = Arguments.createMap();
        result.putString("username", info.getUserName());
        result.putString("nickname", info.getNickname());
        result.putString("avatar", info.getAvatar());
        result.putString("username", info.getUserName());
        result.putInt("gender", messagePropsToInt(Utils.defaultValue(info.getGender(), UserInfo.Gender.unknown)));
        result.putString("genderDesc", messagePropsToString(Utils.defaultValue(info.getGender(), UserInfo.Gender.unknown)));
        result.putDouble("birthday", info.getBirthday());
        result.putString("region", info.getRegion());
        result.putString("signature", info.getSignature());
        result.putString("noteName", info.getNotename());
        result.putString("noteText", info.getNoteText());
        promise.resolve(result);
    }
    /**
     * 发送单聊消息
     * @param username  用户名
     * @param type      消息类型
     * @param data      数据
     * @param promise
     */
    @ReactMethod
    public void sendSingleMessage(String appkey, String username, String type, ReadableMap data, final Promise promise) {
        Conversation conversation = Utils.isEmpty(appkey)
                ? Conversation.createSingleConversation(username)
                : Conversation.createSingleConversation(username, appkey);
        sendMessage(conversation, type, data, "1", username, appkey, promise);
    }
    /**
     * 发送群聊消息
     * @param groupId   群号
     * @param type      消息类型
     * @param data      数据
     * @param promise
     */
    @ReactMethod
    public void sendGroupMessage(String groupId, String type, ReadableMap data, final Promise promise) {
        long gid;
        try {
            gid = Long.valueOf(groupId);
        } catch (NumberFormatException e) {
            JMessageException ex = JMessageException.ILLEGAL_ARGUMENT_EXCEPTION;
            promise.reject(ex.getCode(), ex.getMessage());
            return;
        }
        Conversation conversation = Conversation.createGroupConversation(gid);
        sendMessage(conversation, type, data, "", groupId, null, promise);
    }
    /**
     * 根据会话发送消息
     * @param cid
     * @param type
     * @param data
     * @param promise
     */
    @ReactMethod
    public void sendMessageByCID(String cid, String type, ReadableMap data, final Promise promise) {
        try {
            Conversation conversation = getConversation(cid);
            sendMessage(conversation, type, data, "1", null, null, promise);
        } catch (JMessageException e) {
            promise.reject(e.getCode(), e.getMessage());
        }
    }
    /**
     * 获取会话列表
     * @param promise
     */
    @ReactMethod
    public void allConversations(final Promise promise) {
        List<Conversation> conversations = JMessageClient.getConversationList();
        WritableArray result = Arguments.createArray();
        for (Conversation conversation: conversations) {
            WritableMap conversationMap = transformToWritableMap(conversation);
            result.pushMap(conversationMap);
        }
        promise.resolve(result);
    }
    /**
     * 获得历史消息
     * @param cid       会话id
     * @param offset    从最新开始的偏移
     * @param limit     数量
     * @param promise
     */
    @ReactMethod
    public void historyMessages(String cid, Integer offset, Integer limit, final Promise promise) {
        Integer _limit = limit <= 0 ? Integer.MAX_VALUE : limit;
        try {
            Conversation conversation = getConversation(cid);
            WritableArray result = Arguments.createArray();
            for (Message message: conversation.getMessagesFromNewest(offset, _limit)) {
                result.pushMap(transformToWritableMap(message));
            }
            promise.resolve(result);
        } catch (JMessageException e) {
            promise.reject(e.getCode(), e.getMessage());
        }
    }
    /**
     * 清除未读记录数
     * @param cid       会话id
     * @param promise
     */
    @ReactMethod
    public void clearUnreadCount(String cid, final Promise promise) {
        try {
            Conversation conversation = getConversation(cid);
            int unreadCount = conversation.getUnReadMsgCnt();
            if (conversation.resetUnreadCount()) {
                promise.resolve(unreadCount);
            } else {
                promise.reject("", "");
            }
        } catch (JMessageException e) {
            promise.reject(e.getCode(), e.getMessage());
        }
    }
    /**
     * 删除会话
     * @param cid       会话id
     * @param promise
     */
    @ReactMethod
    public void removeConversation(String cid, final Promise promise) {
        try {
            Conversation conversation = getConversation(cid);
            switch (conversation.getType()) {
                case single:
                    UserInfo userInfo = (UserInfo)conversation.getTargetInfo();
                    JMessageClient.deleteSingleConversation(userInfo.getUserName());
                case group:
                    GroupInfo groupInfo = (GroupInfo)conversation.getTargetInfo();
                    JMessageClient.deleteGroupConversation(groupInfo.getGroupID());
            }
            promise.resolve(null);
        } catch (JMessageException e) {
            promise.reject(e.getCode(), e.getMessage());
        }
    }
    /**
    * 进入会话
    * @param targetId       会话id
    * @param appKey
    * @param groupId
    */
    @ReactMethod
    public void enterConversation(String targetId, String appKey, String groupId) {
        if (null != targetId && !TextUtils.isEmpty(targetId)) {
            JMessageClient.enterSingleConversation(targetId, appKey);
        } else {
            JMessageClient.enterGroupConversation(Long.parseLong(groupId));
        }
    }

    /**
    * 用户登录状态
    * @param event
    */
    public void onEvent(LoginStateChangeEvent event){
        LoginStateChangeEvent.Reason reason = event.getReason();//获取变更的原因
        WritableMap map = Arguments.createMap();
        //UserInfo myInfo = event.getMyInfo();//获取当前被登出账号的信息
        //map.putString("username", myInfo.getUserName());

        switch (reason) {
            case user_password_change:
                //用户密码在服务器端被修改
                map.putInt("status", 11);
                break;
            case user_logout:
                //用户换设备登录
                map.putInt("status", 12);
                break;
            case user_deleted:
                //用户被删除
                map.putInt("status", 13);
                break;
        }

        this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                                                                    .emit("onLoginStateChange", map);
    }

     /**
     * 接收通知栏点击事件
     * @param event
     */
    public void onEvent(NotificationClickEvent notificationClickEvent){
        if (null == notificationClickEvent) {
            return;
        }
        Message msg = notificationClickEvent.getMessage();
        if (msg != null) {
            WritableMap map = Arguments.createMap();
            switch (msg.getTargetType()) {
                case single:
                    UserInfo userInfo = (UserInfo)msg.getTargetInfo();
                    map.putInt("type", 1);
                    map.putString("id", userInfo.getUserName());
                    map.putString("title", userInfo.getNickname());
                    map.putString("appKey", userInfo.getAppKey());
                    map.putInt("isNoDisturb", userInfo.getNoDisturb());
                    break;
                case group:
                    GroupInfo groupInfo = (GroupInfo)msg.getTargetInfo();
                    map.putInt("type", 2);
                    map.putDouble("id", groupInfo.getGroupID());
                    map.putString("title", groupInfo.getGroupName());
                    map.putInt("isNoDisturb", groupInfo.getNoDisturb());
                    map.putString("owner", groupInfo.getGroupOwner());
                    break;
                default:
                    break;
            }

            this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("onNotificationClick", map);
        }
    }

    /**
     * 接收消息事件监听
     * @param event
     */
    public void onEvent(MessageEvent event) {
        Message message = event.getMessage();
        this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("onReceiveMessage", transformToWritableMap(message));
    }

    /**
     * 接收offline消息事件监听
     * @param event
     */
    public void onEvent(OfflineMessageEvent event) {
        List<Message> messages = event.getOfflineMessageList();
        for (Message message: messages) {
            this.getReactApplicationContext().getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("onReceiveMessage", transformToWritableMap(message));
        }
    }

    private WritableMap transformToWritableMap(Message message) {
        WritableMap result = Arguments.createMap();
        if (message == null) return result;

        switch (message.getContentType()){
            case text:
                //处理文字消息
                result.putString("content", message.getContent().toJson());
                break;
            case custom:
                //处理自定义消息
                result.putString("content", message.getContent().toJson());
                break;
            case eventNotification:
                return result; // todo: 直接屏蔽掉事件提示, 因为ios没有具体事件...
                //EventNotificationContent eventNotificationContent = (EventNotificationContent)message.getContent();
                //处理事件提醒消息
                //result.putString("content", eventNotificationContent.getEventText());
                //break;
        }
        result.putString("msgId", Utils.defaultValue(message.getId(), "").toString());
        result.putString("serverMessageId", Utils.defaultValue(message.getServerMessageId(), "").toString());

        WritableMap from = Arguments.createMap();
        from.putString("type", message.getFromType());
        from.putString("appKey", message.getFromUser().getAppKey());
        from.putInt("isNoDisturb", message.getFromUser().getNoDisturb());
        from.putString("id", message.getFromUser().getUserName());
        from.putString("name", message.getFromUser().getUserName());
        from.putString("nickname", message.getFromUser().getNickname());
        result.putMap("from", from);

        WritableMap target = Arguments.createMap();
        target.putInt("type", messagePropsToInt(message.getTargetType()));
        target.putString("typeDesc", messagePropsToString(message.getTargetType()));
        switch (message.getTargetType()) {
            case single:
                UserInfo userInfo = (UserInfo)message.getTargetInfo();
                target.putString("name", userInfo.getUserName());
                target.putString("id", userInfo.getUserName());
                target.putString("appKey", userInfo.getAppKey());
                target.putInt("isNoDisturb", userInfo.getNoDisturb());
                target.putString("nickname", userInfo.getNickname());
                break;
            case group:
                GroupInfo groupInfo = (GroupInfo)message.getTargetInfo();
                target.putString("name", groupInfo.getGroupName());
                target.putDouble("gid", groupInfo.getGroupID());
                target.putInt("isNoDisturb", groupInfo.getNoDisturb());
                target.putString("description", groupInfo.getGroupDescription());
                target.putString("owner", groupInfo.getGroupOwner());
                break;
            default:
                break;
        }
        result.putMap("target", target);
        result.putDouble("timestamp", message.getCreateTime());
        result.putInt("contentType", messagePropsToInt(message.getContentType()));
        result.putString("contentTypeDesc", messagePropsToString(message.getContentType()));

        return result;
    }

    private WritableMap transformToWritableMap(Conversation conversation) {
        WritableMap result = Arguments.createMap();
        File avatar = conversation.getAvatarFile();
        if (avatar != null) {
            String imageBase64 = Utils.base64Encode(avatar, Bitmap.CompressFormat.PNG);
            result.putString("avatar", imageBase64);
        }
        result.putInt("type", messagePropsToInt(conversation.getType()));
        result.putString("typeDesc", messagePropsToString(conversation.getType()));
        result.putString("title", conversation.getTitle());

        result.putString("laseMessage", getLastMessageContent(conversation));
        result.putInt("unreadCount", conversation.getUnReadMsgCnt());

        ConversationIDJSONModel conversationIDJSONModel = new ConversationIDJSONModel();
        conversationIDJSONModel.setAppkey(conversation.getTargetAppKey());
        conversationIDJSONModel.setType(messagePropsToInt(conversation.getType()));

        switch (conversation.getType()) {
            case single:
                UserInfo userInfo = (UserInfo)conversation.getTargetInfo();
                result.putString("username", userInfo.getUserName());
                conversationIDJSONModel.setId(userInfo.getUserName());
                break;
            case group:
                GroupInfo groupInfo = (GroupInfo)conversation.getTargetInfo();
                result.putDouble("groupId", groupInfo.getGroupID());
                conversationIDJSONModel.setId(String.valueOf(groupInfo.getGroupID()));
                break;
            default:
                break;
        }
        Gson gson = new Gson();
        result.putString("id", gson.toJson(conversationIDJSONModel));
        return result;
    }

    private String messagePropsToString(UserInfo.Gender gender) {
        if (gender == null) return null;
        switch (gender) {
            case unknown:
                return "Unknown";
            case male:
                return "Male";
            case female:
                return "Female";
            default:
                return null;
        }
    }

    private String messagePropsToString(ConversationType type) {
        if (type == null) return null;
        switch (type) {
            case single:
                return "Single";
            case group:
                return "Group";
            default:
                return null;
        }
    }

    private String messagePropsToString(ContentType type) {
        if (type == null) return null;
        switch (type) {
            case unknown:
                return "Unknown";
            case text:
                return "Text";
            case image:
                return "Image";
            case voice:
                return "Voice";
            case custom:
                return "Custom";
            case eventNotification:
                return "Event";
            case file:
                return "File";
            case location:
                return "Location";
            default:
                return null;
        }
    }

    private Integer messagePropsToInt(UserInfo.Gender gender) {
        if (gender == null) return null;
        switch (gender) {
            case unknown:
                return 0;
            case male:
                return 1;
            case female:
                return 2;
            default:
                return null;
        }
    }

    private Integer messagePropsToInt(ConversationType type) {
        if (type == null) return null;
        switch (type) {
            case single:
                return 1;
            case group:
                return 2;
            default:
                return null;
        }
    }

    private Integer messagePropsToInt(ContentType type) {
        if (type == null) return null;
        switch (type) {
            case unknown:
                return 0;
            case text:
                return 1;
            case image:
                return 2;
            case voice:
                return 3;
            case custom:
                return 4;
            case eventNotification:
                return 5;
            case file:
                return 6;
            case location:
                return 7;
            default:
                return null;
        }
    }

    private String getMetaData(String name) {
        ReactApplicationContext reactContext = getReactApplicationContext();
        try {
            ApplicationInfo appInfo = reactContext.getPackageManager()
                    .getApplicationInfo(reactContext.getPackageName(),
                            PackageManager.GET_META_DATA);
            return appInfo.metaData.get(name).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getLastMessageContent(Conversation conversation) {
        Message message = conversation.getLatestMessage();
        if(message == null) return "";
        switch (message.getContentType()) {
            case unknown:
                return "[未知]";
            case text:
                return ((TextContent)message.getContent()).getText();
            case image:
                return "[图片]";
            case voice:
                return "[语音]";
            case custom:
                return "[自定义]";
            case eventNotification:
                return "[事件]";
            case file:
                return "[文件]";
            case location:
                return "[位置]";
            default:
                return "";
        }
    }

    private void sendMessage(Conversation conversation,
                             String contentType,
                             ReadableMap data,
                             String isSingle,
                             String id,
                             String appKey,
                             final Promise promise
                             ) {
        String type = contentType.toLowerCase();
        MessageContent content;

        if (conversation == null) {
            JMessageException ex = JMessageException.CONVERSATION_INVALID;
            promise.reject(ex.getCode(), ex.getMessage());
            return;
        }
        switch (type) {
            case "text":
                content = new TextContent(data.getString("text"));
                break;
            case "image":
                try {
                    File imageFile = new File(data.getString("image"));
                    content = new ImageContent(imageFile);
                } catch (FileNotFoundException e) {
                    JMessageException ex = JMessageException.MESSAGE_CONTENT_NULL;
                    promise.reject(ex.getCode(), ex.getMessage());
                    return;
                }
                break;
            default:
                JMessageException ex = JMessageException.MESSAGE_CONTENT_TYPE_NOT_SUPPORT;
                promise.reject(ex.getCode(), ex.getMessage());
                return;
        }

        content.setStringExtra("id", id);
        content.setStringExtra("appkey", appKey);
        content.setStringExtra("isSingle", isSingle);
        final Message message = conversation.createSendMessage(content);
        message.setOnSendCompleteCallback(new BasicCallback() {
            @Override
            public void gotResult(int responseCode, String responseDesc) {
                if (responseCode == 0) {
                    promise.resolve(transformToWritableMap(message));
                } else {
                    promise.reject(String.valueOf(responseCode), responseDesc);
                }
            }
        });
        MessageSendingOptions options = new MessageSendingOptions();
        options.setCustomNotificationEnabled(true);
        options.setRetainOffline(true);

        if(data.getString("not_at") != null){
            options.setNotificationAtPrefix(data.getString("not_at"));
        }
        if(data.getString("not_text") != null){
            options.setNotificationText(data.getString("not_text"));
        }
        if(data.getString("not_title") != null){
            options.setNotificationTitle(data.getString("not_title"));
        }
        if(data.getString("not_at") == null && data.getString("not_text") == null && data.getString("not_title") == null){
            options = null;
        }
        if(options == null){
            JMessageClient.sendMessage(message);
        }else{
            JMessageClient.sendMessage(message, options);
        }
    }

    private Conversation getConversation(String cid) throws JMessageException {
        if (Utils.isEmpty(cid)) {
            throw JMessageException.CONVERSATION_ID_EMPTY;
        }
        ConversationIDJSONModel conversationIDJSONModel;
        try {
            Gson gson = new Gson();
            conversationIDJSONModel = gson.fromJson(cid, ConversationIDJSONModel.class);
        } catch (JsonParseException ex) {
            throw JMessageException.CONVERSATION_INVALID;
        }
        String appkey = conversationIDJSONModel.getAppkey();
        String nameOrGID = conversationIDJSONModel.getId();
        Integer type = conversationIDJSONModel.getType();

        if (Utils.isEmpty(nameOrGID) || type == null) {
            throw JMessageException.CONVERSATION_INVALID;
        }
        Conversation conversation = null;
        switch (type) {
            case 1:
                conversation = Utils.isEmpty(appkey)
                        ? Conversation.createSingleConversation(nameOrGID)
                        : Conversation.createSingleConversation(nameOrGID, appkey);
                break;
            case 2:
                conversation = Conversation.createGroupConversation(Long.valueOf(nameOrGID));
                break;
            default:
                break;
        }
        if (conversation == null) {
            throw JMessageException.CONVERSATION_INVALID;
        }
        return conversation;
    }
}
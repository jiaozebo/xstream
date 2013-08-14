package com.crearo.mpu.sdk;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import junit.framework.Assert;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import util.CommonMethod;
import util.DES;
import util.MD5;
import util.XMLParser;
import vastorager.StreamWriter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.CRAudioTrack;
import android.util.Log;
import c7.C7Message;
import c7.CRChannel;
import c7.ChannelCallback;
import c7.DC7;
import c7.IResType;
import c7.LoginInfo;
import c7.MessageReq;
import c7.NC7;
import c7.NCAssist;
import c7.OptID;
import c7.Param;

import com.crearo.mpu.sdk.client.ErrorCode;

/**
 * @author John
 * @version 1.0
 * @date 2011-11-15
 */
public class MPUHandler extends Handler implements ChannelCallback {
	public static final int MSG_NC_MESSAGE_FETCHED = 0x1000;
	public static final int MSG_DC_DATA_FETCHED = 0x1001;
	public static final int MSG_CHANNEL_ERROR = 0x1002;
	// public static final int MSG_CREATE_ERROR = 0x1003;
	public static final int MSG_REND_ERROR = 0x1004;
	public static final int MSG_ERROR_END = 0x1004;
	private static final String KEY_IA_SYNC = "key_ia_sync";
	private static final String KEY_FIX_ADDR = "key_fixAddr";
	private static final String KEY_RECORD_INTERVAL = "key_record_interval";
	private static final String KEY_AUTO_RERECORD = "key_auto_rerecord";
	private NCCAllback mChannelCallback;

	public interface RendCallback {

		public static final byte STT_REND_BEGIN = 0x00;
		public static final byte STT_REND_END = 0x01;

		/**
		 * 对讲
		 */
		public static final byte OA_TYPE_TALK = 0x10;
		/**
		 * 喊话
		 */
		public static final byte OA_TYPE_CALL = 0x20;

		/**
		 * p2p对讲
		 */
		public static final byte OA_TYPE_P2P_TALK = 0x04;
		/**
		 * 集群对讲
		 */
		public static final byte OA_TYPE_TEAM_TALK = 0x08;

		/**
		 * 渲染状态的回调
		 * 
		 * @param type
		 *            资源类型
		 * @param status
		 *            当不是双向对讲时，为STT_REND_END与STT_REND_BEGIN两种状态，
		 *            否则为OA_TYPE与这两种状态的和。
		 */
		void onRendStatusFetched(IResType type, byte status);
	}

	public interface RecordCallback {

		public static final int STT_RECORD_BEGIN = 0;
		public static final int STT_RECORD_END = 1;

		/**
		 * 录像状态的回调
		 * 
		 * @param status
		 *            {@link #STT_RECORD_BEGIN}表示录像开始，{@link #STT_RECORD_END}
		 *            表示录像结束
		 */
		void onRecordStatusFetched(int status);

	}

	public abstract class NCCAllback implements ChannelCallback {

		@Override
		public int createError(CRChannel arg0, int arg1) {
			return 0;
		}

		@Override
		public int onPakageFetched(CRChannel arg0, ByteBuffer arg1) {
			return 0;
		}

	}

	public static final int MSG_RECORD_ERROR = MSG_ERROR_END + 1;
	public static final int MSG_ARG1_RECORD_MANUAL_CLOSE = MSG_ERROR_END + 2;
	protected GPSHandler gpsHandler;

	private RecordCallback mRecordCallback;
	private RendCallback mRendCallback;
	private static final NC7 sNc = new NC7();
	protected Context mContext;
	// private Thread mNCThread;
	public static String sCUID;

	private static final boolean handleOA = false;
	private static final String TAG = "MPUHandler";
	private static final String KEY_PUID = "key_puid";

	public MPUHandler(Context context) {
		Assert.assertNotNull(context);
		mContext = context;

		CRAudioTrack.sUseSimpleEchoCanceller = false;
	}

	public int login(LoginInfo info) {
		sNc.setCallback(this);
		for (IResType type : IResType.values()) {
			type.mIsAlive = false;
		}
		info.binPswHash = MD5.encrypt(info.password.getBytes());
		int rst = sNc.create(info);
		if (rst != 0) {
			rst += ErrorCode.NC_OFFSET;
		}
		return rst;
	}

	public boolean isLogined() {
		return sNc.isActive();
	}

	public void close() {
		mRecordCallback = null;
		mRendCallback = null;
		mChannelCallback = null;
		// mSpeex.echo_destory();
		// 先把NC断了，这样就不会在关闭了dc后，NC又收到申请流命令了。
		sNc.close();
		if (StreamWriter.singleton().isActive()) {
			handleRecordFile();
		}
		VideoRunnable vr = VideoRunnable.singleton();
		if (vr != null) {
			DC7 dc7 = vr.getVideoDC();
			if (dc7 != null) {
				dc7.close();
			}
			vr.close();
		}
		DC7 dc7 = AudioRunnable.singleton().getDc();
		if (dc7 != null) {
			dc7.close();
		}
		AudioRunnable.singleton().stop();
		dc7 = OAudioRunnable.singleton().getDC();
		if (dc7 != null) {
			dc7.close();
		}
		OAudioRunnable.singleton().stop();
		if (gpsHandler != null) {
			dc7 = gpsHandler.getDC();
			if (dc7 != null) {
				dc7.close();
			}
			gpsHandler.close();
		}
		for (IResType it : IResType.values()) {
			it.mIsAlive = false;
		}
	}

	@Override
	public void handleMessage(Message msg) {
		super.handleMessage(msg);
		{
			switch (msg.what) {
			case MSG_NC_MESSAGE_FETCHED:
				ByteBuffer buffer = (ByteBuffer) msg.obj;
				Assert.assertNotNull(buffer);
				C7Message message = parserMessage(buffer);
				message.tran_id = NCAssist.getTransId(buffer);
				// Log.d(TAG, message.toString());
				handleMessage(message);
				break;
			case MSG_REND_ERROR:
			case MSG_CHANNEL_ERROR: {
				int errorCode = msg.arg1;
				CRChannel channel = (CRChannel) msg.obj;
				handleChannelError(channel, errorCode);
			}
				break;
			default:
				break;
			}
		}
		if (msg.what == MSG_RECORD_ERROR) {
			Assert.assertTrue(StreamWriter.singleton().isActive());
			handleRecordFile();
			Log.d(TAG, String.format("StreamWriter write error: %d.", msg.arg1));
			// 自动接着录像
			// && Common.getDefaultBoolean(SetActivity.KEY_AUTO_RERECORD,
			// context, true)
			// -102~-105之间，符合换文件的条件。
			SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(mContext);
			boolean autoRecord = prf.getBoolean(KEY_AUTO_RERECORD, false);
			if (autoRecord && msg.arg1 >= StreamWriter.CRSW_ERROR_CHGFILE_BAD_SEQUENCE
					&& msg.arg1 <= StreamWriter.CRSW_ERROR_CHGFILE_EXCEED_INTERVAL) {
				handleRecordFile();
			}
		}
	}

	public void handleRecordFile() {

		int stt = RecordCallback.STT_RECORD_END;
		StreamWriter streamWriter = StreamWriter.singleton();
		if (streamWriter.isActive()) {
			VideoRunnable.singleton().setRecorder(null);
			AudioRunnable.singleton().setRecorder(null);
			if (AudioRunnable.singleton().getDc() == null) {
				AudioRunnable.singleton().stop();
			}
			if (handleOA) {
				OAudioRunnable.singleton().setRecorder(null);
			}

			streamWriter.close();
			stt = RecordCallback.STT_RECORD_END;
		} else {
			VideoRunnable.singleton().setHandler(this);
			SimpleDateFormat sdf = new SimpleDateFormat("yy-MM-dd HH.mm.ss", Locale.CHINA);
			String path = String.format("%s/%s.avi", Common.PATH_STORAGE_RECORD,
					sdf.format(new Date()));
			SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(mContext);
			int recordIntervalInMin = Integer.parseInt(prf.getString(KEY_RECORD_INTERVAL,
					String.valueOf(5)));
			int nRet = streamWriter.create(path, recordIntervalInMin * 60 * 1000);
			if (nRet == 0) {
				VideoRunnable.singleton().setRecorder(streamWriter);
				if (VideoRunnable.singleton() instanceof PreviewRunnable) { // 硬编码时可能会导致录像死锁，原因未知，因此暂时注释
					DC7 dc = AudioRunnable.singleton().getDc();
					startIAWithDC(dc);
					AudioRunnable.singleton().setRecorder(streamWriter);
				}
				if (handleOA) {
					if (OAudioRunnable.singleton().isActive()) {
						OAudioRunnable.singleton().setRecorder(streamWriter);
					}
				}
				stt = RecordCallback.STT_RECORD_BEGIN;
			}
		}
		if (mRecordCallback != null) {
			mRecordCallback.onRecordStatusFetched(stt);
		}
	}

	/**
	 * 预处理申请流命令。
	 * <p>
	 * 如果允许该流，那么会直接创建DC通道。
	 * 
	 * @param info
	 * @return 0表示成功，否则告知平台错误信息，比如不支持该操作，返回 ERROR_UNSUPPORT_OPERATION
	 */
	protected int handleStartStream(LoginInfo info) {
		if (info.resType.mIsAlive) {
			return ErrorCode.ERROR_REOURCE_IN_USE;
		}
		info.resType.mIsAlive = true;
		SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(mContext);
		boolean isFixAddr = prf.getBoolean(KEY_FIX_ADDR, false);
		if (isFixAddr) {
			info.addr = sNc.getLoginInfo().addr;
		}
		DC7 dc = new DC7(info.resType.name());
		dc.setCallback(this).create(info);
		dc.start();
		// 因为GPS要在主线程中创建，因此在这里create，在回调里不用再创建了
		if (info.resType == IResType.GPS) {
			gpsHandler = new GPSHandler(mContext.getApplicationContext(), dc);
			// GPSHandler.singleton().create((LocationManager) info.param1, dc);
		}
		return 0;
	}

	public int handleStartKeyFrame(int count) {
		VideoRunnable runnable = VideoRunnable.singleton();
		if (runnable != null) {
			runnable.startKeyFrame(count);
		}
		return 0;
	}

	@Override
	public int createError(CRChannel channel, final int errorCode) {
		Assert.assertTrue(channel instanceof DC7);
		final DC7 dc = (DC7) channel;
		post(new Runnable() {

			@Override
			public void run() {
				if (errorCode != 0) {
					dc.close();
					LoginInfo info = dc.getLoginInfo();
					info.resType.mIsAlive = false;
				} else {
					handleStartWork(dc);
				}
			}
		});
		return 0;
	}

	/**
	 * 表示某个流的DC通道创建成功了。
	 * <p>
	 * 这里会将DC通道绑定在某个资源上，将流通过dc通道发送（pump）或者通过DC通道接收流
	 * 
	 * @param dc
	 */
	protected void handleStartWork(DC7 dc) {
		LoginInfo info = dc.getLoginInfo();
		Log.d(TAG, info.resType + "channel built。");

		switch (info.resType) {
		case IV:
			VideoRunnable runnable = VideoRunnable.singleton();
			if (runnable != null) {
				runnable.setVideoDC(dc);
			} else {
				dc.close();
				return;
			}
			break;
		case IA: {
			startIAWithDC(dc);
		}
			break;
		case OA:
			if (info.param1.equals(OptID.CTL_OA_StartTalk_PushMode.toString())) {
				Log.i(TAG, "startTalk:" + info.toString());
				startIAWithDC(dc);
				startOAWithDC(dc);
			} else if (info.param1.equals(OptID.CTL_OA_StartCall_PushMode.toString())) {
				Log.i(TAG, "startCall:" + info.toString());
				startOAWithDC(dc);
			} else if (info.param1.equals(OptID.CTL_OA_StartTeamTalk_PushMode.toString())) {
				Log.i(TAG, "startTeamTalk:" + info.toString());
				startIAWithDC(dc);
				startOAWithDC(dc);
			}
			break;
		case GPS: {
			// 已经在runDataChannel里创建过了。
		}
			break;
		default:
			break;
		}
		if (mRendCallback != null) {
			byte status = RendCallback.STT_REND_BEGIN;
			if (info.param1.equals(OptID.CTL_OA_StartTalk_PushMode.toString())) {
				status |= RendCallback.OA_TYPE_TALK;
			} else if (info.param1.equals(OptID.CTL_OA_StartTeamTalk_PushMode.toString())) {
				status |= RendCallback.OA_TYPE_TEAM_TALK;
			}
			mRendCallback.onRendStatusFetched(info.resType, status);
		}
	}

	public void startOAWithDC(DC7 dc) {
		OAudioRunnable audioRunnable = OAudioRunnable.singleton();
		audioRunnable.setHandler(this);
		audioRunnable.setDc(dc);
		int nRet = audioRunnable.start();
		if (nRet == 0) {
			if (handleOA) {
				// 如果正在录像，那么也录向OA
				StreamWriter sw = StreamWriter.singleton();
				if (sw.isActive()) {
					audioRunnable.setRecorder(sw);
				}
			}
		} else {
			onErrorFetched(dc, ErrorCode.ERROR_REOURCE_IN_USE);
		}
	}

	public void startIAWithDC(DC7 dc) {
		// 如果已经正在工作了（比如正在录像了），就只设置一下DC便可
		AudioRunnable ar = AudioRunnable.singleton();
		if (ar.isActive()) {
			if (ar.getDc() == null) {
				ar.setDc(dc);
			} else {
				onErrorFetched(dc, ErrorCode.ERROR_REOURCE_IN_USE);
			}
			return;
		}
		ar.setDc(dc);
		final int result = ar.start();
		if (result == 0) {
			SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(mContext);
			boolean isSync = prf.getBoolean(KEY_IA_SYNC, false);
			if (isSync) {
			}
		}
	}

	@Override
	public int onPakageFetched(CRChannel dataChannel, ByteBuffer buffer) {
		// 在此需要判断是nc通道接收来的还是dc通道接收来的。
		if (dataChannel == sNc) {
			Assert.assertTrue(NCAssist.isLegal(buffer));
			buffer.position(NC7.MSG_HEAD_LEN);
			if (NCAssist.isCrypt(buffer)) {
				Assert.assertTrue(DES.decrypt(buffer, sNc.getCryptKey()));
			}
			Message msg = obtainMessage(MSG_NC_MESSAGE_FETCHED, buffer);
			msg.sendToTarget();
		} else {
			// 这里肯定是dc接收来的数据了。
			// 对于MPU，一般是用DC发数据，只有OA才用DC接收数据.
			if (dataChannel == OAudioRunnable.singleton().getDC()) {
				OAudioRunnable.singleton().pumpAudioFrame(buffer.array(), buffer.position(),
						buffer.remaining());
			}
		}
		return FRAMES_OVERFLOW_IGNORE;
	}

	@Override
	public void onErrorFetched(CRChannel channel, int errorCode) {
		Message msg = obtainMessage(MSG_CHANNEL_ERROR, channel);
		msg.arg1 = errorCode;
		sendMessage(msg);
	}

	public void handleChannelError(CRChannel channel, int errorCode) {
		channel.close();
		if (channel == sNc) {
			Log.d(TAG, "channel build error ：" + errorCode);
			if (mChannelCallback != null) {
				mChannelCallback.onErrorFetched(channel, errorCode);
				return;
			}
		} else {
			LoginInfo info = channel.getLoginInfo();
			Log.d(TAG, info.resType + " chanel error ：" + errorCode);
			info.resType.mIsAlive = false;
			switch (info.resType) {
			case IV: {
				VideoRunnable vr = VideoRunnable.singleton();
				if (vr != null) {
					VideoRunnable.singleton().setVideoDC(null);
				}
			}
				break;
			case OA: {
				// 这里有喊话和对讲两种
				// 不用关心录像，直接关闭就可以了
				OAudioRunnable.singleton().stop();
				AudioRunnable ar = AudioRunnable.singleton();
				if (ar.isActive()) {
					ar.setEchoCanceller(null);
					// mSpeex.echo_reset();
				}

				// 如果是对讲的话，不只要把OA关闭，还要把IA关闭。
				if (OptID.CTL_OA_StartTalk_PushMode.toString().equals(info.param1)
						|| OptID.CTL_OA_StartTeamTalk_PushMode.toString().equals(info.param1)) {
					// next case
				} else {
					break;
				}

			}
			case IA: {
				// 如果正在录像的话，先不关闭IA，只将DC设置为NULL
				if (AudioRunnable.singleton().getStreamWriter() == null) {
					AudioRunnable.singleton().stop();
				} else {
					AudioRunnable.singleton().setEchoCanceller(null);
					AudioRunnable.singleton().setDc(null);
				}
			}
				break;
			case GPS: {
				if (gpsHandler != null) {
					gpsHandler.close();
				}
			}
				break;
			default:
				Assert.assertTrue(info.resType.toString(), false);
				break;
			}
			byte status = RendCallback.STT_REND_END;
			// OA有好几种情况
			if (info.resType == IResType.OA) {
				// 如果是对讲的话，不只要把OA按钮状态更改，还要把IA按钮状态更改。
				if (OptID.CTL_OA_StartTalk_PushMode.toString().equals(info.param1)) {
					status |= RendCallback.OA_TYPE_TALK;
				} else if (OptID.CTL_OA_StartTeamTalk_PushMode.toString().equals(info.param1)) {
					status |= RendCallback.OA_TYPE_TEAM_TALK;
				}
			}
			if (mRendCallback != null) {
				mRendCallback.onRendStatusFetched(info.resType, status);
			}
		}

	}

	public void sendRespWithNC(int tran_id, byte[] data) {
		sNc.sendResponse(tran_id, data);
	}

	public void setRecordCallback(RecordCallback rc) {
		mRecordCallback = rc;
	}

	public void setRendCallback(RendCallback rc) {
		mRendCallback = rc;
	}

	public RendCallback getRendCallback() {
		return mRendCallback;
	}

	protected int handleSetFrameRate(int frameRate) {
		Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
		editor.putInt(Common.KEY_FRAME_RATE, frameRate).commit();
		VideoRunnable vr = VideoRunnable.singleton();
		if (vr != null) {
			vr.setFrameRate(frameRate);
		}
		return 0;
	}

	public void setChannelCallback(NCCAllback channelCallback) {
		this.mChannelCallback = channelCallback;
	}

	private void handleMessage(C7Message msg) {
		if (msg instanceof MessageReq) {
			OptID id = OptID.UnSupport_Operation;
			try {
				id = OptID.valueOf(msg.cmd.dstRes.optId);
			} catch (Exception e) {
				e.printStackTrace();
			}

			int nErrorCode = 0;
			String resType = "OA";
			switch (id) {
			// 为了避免创建两条通道，仅支持实时流。

			case CTL_COMMONRES_StartStream_PushMode:
				resType = "IV";
				if (!msg.cmd.dstRes.param.streamType.equalsIgnoreCase(C7Message.REALTIME)) {
					nErrorCode = ErrorCode.ERROR_UNSUPPORT_OPERATION;
					break;
				}
			case CTL_OA_StartTeamTalk_PushMode:
			case CTL_OA_StartTalk_PushMode:
			case CTL_OA_StartCall_PushMode: {
				LoginInfo info = new LoginInfo();
				info.addr = msg.cmd.dstRes.param.ip;
				info.resType = IResType.valueOf(msg.cmd.dstRes.type);
				info.port = CommonMethod.parseInt(msg.cmd.dstRes.param.port, -1);
				info.token = msg.cmd.dstRes.param.token;

				SharedPreferences prf = PreferenceManager.getDefaultSharedPreferences(mContext);
				String puid = prf.getString(KEY_PUID, null);
				if (puid == null) {
					puid = Common.getPuid(mContext);
				}
				info.chID = String.format("%s:%s:%d", puid, resType, 0);
				info.chData = createChData(id, 1, "AMR");
				info.param1 = id.name();
				nErrorCode = handleStartStream(info);
			}
				break;
			case CTL_IV_StartKeyFrame: {
				nErrorCode = handleStartKeyFrame(1);
			}
				break;
			case CFG_OA_DecoderProducerID: {
				nErrorCode = 0;
			}
				break;
			case CFG_IV_FrameRate: {
				if (msg.cmd.type.equals("SET")) {
					nErrorCode = handleSetFrameRate(msg.cmd.dstRes.param.mFramePerSecend);
				}
			}
				break;
			case CTL_ST_GetBindedCUID:
				if (sCUID == null) {
					nErrorCode = ErrorCode.ERROR_UNSUPPORT_OPERATION;
				} else {
					if (msg.cmd.dstRes.param == null) {
						msg.cmd.dstRes.param = new Param();
					}
					msg.cmd.dstRes.param.value = sCUID;
					nErrorCode = 0;
				}
				break;
			case UnSupport_Operation:
			default:
				nErrorCode = ErrorCode.ERROR_UNSUPPORT_OPERATION;
				break;
			}
			sendResponse(msg, nErrorCode);
		} else {
			Log.i(TAG, msg.name);
		}
	}

	public static String createChData(OptID optID, int producerID, String alg) {
		switch (optID) {
		case CTL_OA_StartTeamTalk_PushMode:
		case CTL_OA_StartTalk_PushMode: {
			XMLParser xml = new XMLParser();
			Node chData = xml.createTag("ChData");
			Node type = xml.add_tag_parent(chData, "Type");
			xml.setValue(type, "PU");
			Node recvAudio = xml.add_tag_parent(chData, "RecvAudio");
			Node pID = xml.add_tag_parent(recvAudio, "ProducerID");
			xml.setValue(pID, "1");
			Node algName = xml.add_tag_parent(recvAudio, "AlgName");
			xml.setValue(algName, alg);
			return XMLParser.node2string(chData);
		}
		case CTL_OA_StartCall_PushMode: {
			XMLParser xml = new XMLParser();
			Node type = xml.createTag("Type");
			xml.setValue(type, "PU");
			return XMLParser.node2string(type);
		}
		case CTL_COMMONRES_StartStream_PushMode:
		default:
			break;
		}
		return null;
	}

	private void sendResponse(C7Message msg, int errorCode) {
		Assert.assertTrue(msg instanceof MessageReq);
		OptID id = OptID.UnSupport_Operation;
		try {
			id = OptID.valueOf(msg.cmd.dstRes.optId);
		} catch (Exception e) {
			e.printStackTrace();
		}
		C7Message res = C7Message.fromName(C7Message.CUCommonMsgRsp);
		res.cmd = msg.cmd;
		res.cmd.nuErrorCode = errorCode + "";
		res.cmd.dstRes.errorCode = errorCode + "";
		switch (id) {
		case CTL_OA_StartTeamTalk_PushMode:
		case CTL_COMMONRES_StartStream_PushMode:
		case CTL_OA_StartTalk_PushMode:
		case CTL_OA_StartCall_PushMode:
			res.cmd.dstRes.param = null;
			break;
		case CTL_IV_StartKeyFrame:
			res.cmd.dstRes.param = null;
			break;
		case CFG_OA_DecoderProducerID:
			res.cmd.dstRes.param.value = "1";
			break;
		case CFG_IV_FrameRate:
			res.cmd.dstRes.param = null;
			break;
		case CTL_ST_GetBindedCUID:
			break;
		default:
			break;
		}
		String message = XMLParser.nodeToStr(res.toNode(), C7Message.UTF_8);
		Log.d(TAG, message);
		sendRespWithNC(msg.tran_id, message.getBytes());
	}

	private static C7Message parserMessage(ByteBuffer buffer) {
		String string = new String(buffer.array(), buffer.position(), buffer.remaining());
		Log.d(TAG, string);
		NodeList nodeList = XMLParser.ParseBuffer(buffer.array(), buffer.position(),
				buffer.remaining());
		Node messageNode = null;
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("Msg")) {
				messageNode = node;
			}
		}
		return messageNode == null ? null : C7Message.fromNode(messageNode);
	}
}

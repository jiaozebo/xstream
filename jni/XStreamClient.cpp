#include <jni.h>
#include"com_john_xstream_XStream.h"
#include <unistd.h>
#include <stdio.h>
#include <arpa/inet.h>
#include <stdlib.h>
#include <errno.h>
#include <string.h>
#include <netdb.h>
#include <sys/types.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <pthread.h>
#include <android/log.h>
#include <assert.h>
#pragma pack (1)

#define MSG_CONNECT_REQUEST 1
#define MSG_CONNECT_RESPONSE 2
#define MSG_CHANNEL_INFO_NOTIFY 15
#define TYPE_CHANNEL_NOTIFY_AV 1
#define TYPE_CHANNEL_NOTIFY_INVALID 0
#define IPHONE_IPCAMERA 0x26E3
#define MAX_NAME_LENGTH 33
#define MAX_IPADDR_LENGTH 16
#define SOCKET int
#define u_int16 unsigned short
#define u_int32 unsigned int
#define u_int8 	unsigned char
#define TAG "XStreamClient"
#define KEY_VIDEO_WIDTH 1000
#define KEY_VIDEO_HEIGHT 1001
#define CMD_CMD_QUIT 0xe450
#define CMD_DATA_QUIT 0xe451
#define CMD_CMD_MESSAGE 0xe452
#define CMD_DATA_MESSAGE 0xe453
#define CMD_CMD_SEND 0xe4ff
#define CMD_DATA_SEND 0xe500

#define ARG_CMD_KEEPALIVE 0x0e
#define ARG_DATA_REQUEST_FRAME 0x05
#define ARG_DATA_REQUEST_END_FRAME 0x5B

struct DATA_SEND_BUFFER {

	typedef struct _TBuffer {
		char *pData;
		int nLength;
		_TBuffer *pNext;
		_TBuffer() {
			pData = NULL;
			nLength = 0;
			pNext = NULL;
		}
	} TBuffer;

	_TBuffer *pHead;
	unsigned int uiLength;

	pthread_mutex_t *pLock;

	DATA_SEND_BUFFER() {
		pHead = NULL;
		uiLength = 0;
		pLock = new pthread_mutex_t;
		int result = pthread_mutex_init(pLock, 0);
		if (result != 0) {
			__android_log_print(ANDROID_LOG_ERROR, TAG, "\n%d", __LINE__);
			assert(0);
		}
	}

	~DATA_SEND_BUFFER() {
		pthread_mutex_destroy(pLock);
		delete pLock;
		pLock = 0;
		while (uiLength > 0) {
			TBuffer *pNext = pHead->pNext;
			delete pHead;
			pHead = pNext;
			--uiLength;
		}
	}

	/**
	 * 这里只保存引用
	 */
	int queue(char *pBuffer, int len) {
		int result = 0;
		pthread_mutex_lock(pLock);
		if (uiLength < 10) {
			result = 1;

			TBuffer *pThis = new TBuffer;
			pThis->pData = pBuffer;
			pThis->nLength = len;
			//加到tail
			unsigned int uiTemp = uiLength;
			TBuffer **pTemp = &pHead;
			while (uiTemp > 0) {
				--uiTemp;
				pTemp = &((*pTemp)->pNext);
			}
			if (*pTemp != NULL) {
				__android_log_print(ANDROID_LOG_ERROR, TAG, "\n%d", __LINE__);
				assert(0);
			}
			*pTemp = pThis;
			++uiLength;
		}
		pthread_mutex_unlock(pLock);
		return result;
	}
	/**
	 * 使用完了后要释放
	 */
	int dequeue(char *&pBuffer, int &len) {
		int result = 0;
		pthread_mutex_lock(pLock);

		if (uiLength > 0) {
			result = 1;
			pBuffer = pHead->pData;
			len = pHead->nLength;
			--uiLength;
			TBuffer *pTemp = pHead->pNext;
			delete pHead;
			pHead = pTemp;
		}

		pthread_mutex_unlock(pLock);
		return result;
	}
};
struct CONTEXT {
	JavaVM *g_jvm;
	jobject weak_this;
	jclass clazz;
	jmethodID msendMessage;

	SOCKET nCmdSocketfd, nDataSocketfd;
	char szDataIP[MAX_IPADDR_LENGTH];
	unsigned short nDataPort;
	unsigned int nSourceID;
	pthread_t tCmd, tData;
	DATA_SEND_BUFFER dataSendBuf;
	unsigned int videoSize[2];
	bool isCmdAlive;
	bool isDataAlive;
};

struct MSG_HEADER //6
{
	unsigned short nCmd;
	int nLength;
};

struct PROXY_HEADER { //17个字节
	unsigned char cType;
	unsigned int dwSourceID;
	unsigned short wSeqNum;
	unsigned char cVersion_StreamID;
	unsigned int dwLength;
	unsigned char cFlag;
	unsigned int dwPlayStamp;
};
struct PRODUCER_INFO //33+4+1+4+4+33=79
{
	PRODUCER_INFO() {
		nManufacturer = 0;
		nCapacity = 0;
		bMulticast = 0;
		nReserved = 0;
		memset(ProducerName, 0, sizeof(ProducerName));
		memset(sPassword, 0, sizeof(sPassword));
	}
	char ProducerName[MAX_NAME_LENGTH];
	unsigned int nCapacity;
	char bMulticast;
	unsigned int nManufacturer;
	unsigned int nReserved;
	char sPassword[MAX_NAME_LENGTH];
};
struct CONN_REQ {
	MSG_HEADER hdr;
	unsigned char ID;
	PRODUCER_INFO pi;
};

struct PRODUCER_RESP {
	unsigned char nResult;
	unsigned int nProducerID;
	unsigned int nReserved;
};
struct CONN_RESP {
	MSG_HEADER hdr;
	PRODUCER_RESP pi;
};

struct CHANNEL_NOTIFY { //15
	MSG_HEADER hdr;
	unsigned char nType;
	unsigned int nCount;
	unsigned int nReserved;
};

struct AV_CHANNEL_INFO { //64
	unsigned int bValid;
	unsigned int nSourceID;
	char ChannelName[MAX_NAME_LENGTH];
	unsigned char nMode;
	char IPAddr[MAX_IPADDR_LENGTH];
	unsigned short nPort;u_int32 bMulticast;u_int32 bVideo;u_int32 bAudio;u_int32 bCradle;
};

typedef struct { //在MSDN中有说明，用于解码视频
	int biSize; // =sizeof(BITMAPINFOHEADER)
	int biWidth; // = 视频图像宽度
	int biHeight; // = 视频图像高度
	short biPlanes; // =1
	short biBitCount; // =24
	int biCompression; // =mmioStringToFOURCC(“H264”, 0)
	int biSizeImage; // = biWidth*biHeight*3
	int biXPelsPerMeter; // =0
	int biYPelsPerMeter; // =0
	int biClrUsed; // =0
	int biClrImportant; // =0
} BITMAPINFOHEADER;
typedef struct video_format {
	u_int16 wLength; //格式块的长度
	u_int8 cFps; //帧频
	BITMAPINFOHEADER bmpHeader;u_int8 formatValid; // 为0时表示formatSpec无效
	char formatSpec[40]; // 为0
	char VideoName[40]; // OSD叠加文字，一般等于NodeName
} VIDEOFORMAT;

typedef struct { //在MSDN中有说明，用于解码音频
	short wFormatTag; //=0x7104（AAC）
	short nChannels; //=2
	int nSamplesPerSec; //=采样率
	int nAvgBytesPerSec; //=采样率*2*2
	short nBlockAlign; //16
	short wBitsPerSample; //2
	short cbSize;
} WAVEFORMATEX;
typedef struct audio_format {
	u_int16 wLength; //格式块的长度, = sizeof( AUDIOFORMAT)
	WAVEFORMATEX waveFormat; //音频流格式，扩展格式数据在AudioFormat后
} AUDIOFORMAT;

struct StreamInfoReq {
//	cStreamType(8)
//	6(8)
//	IPAddr=0(16*8)
//	wIPPort=0(16)
	char cStreamType;
	char cIPMode;
	char IPAddr[16];
	unsigned short wIPPort;
};

struct StreamInfoResp {
	/*
	 cStreamID(8)
	 dwSourceID(32)
	 cStreamType(8)
	 Format Block(128*8)
	 0(13*8),表示13个字节值为0
	 6(8),表示一个字节值为6
	 0(18*8) ,表示18个字节值为0

	 */

	char cStreamID;
	unsigned int dwSourceID;
	/**
	 * 流类型，2是视频流，3是音频流
	 */
	char cStreamType;
	// 128
//	VIDEOFORMAT vFormat;
	char format[128];
	/**
	 * 0(13*8),表示13个字节值为0,6(8),表示一个字节值为6,0(18*8) ,表示18个字节值为0
	 */
	char temp[32];
};

void EncrytString(char *input, int input_len, char *output, int& output_len) {
	char key[MAX_NAME_LENGTH] = "XUNFEIBJXF123*92KG^S)wHT9HFQ<)HT";
	for (int i = 0; i < input_len; i++)
		output[i] = input[i] ^ key[i];
	output_len = input_len;
}

JNIEnv* getJNIEnv(CONTEXT *pContext) {
	JNIEnv* env;
	pContext->g_jvm->AttachCurrentThread(&env, NULL);
	return env;
}

void sendMessage(CONTEXT *pContext, int what, int arg0, int arg1) {
	JNIEnv *penv = getJNIEnv(pContext);
	__android_log_print(ANDROID_LOG_INFO, TAG,
			"getJNIEnv penv:%d, weak_this:%d, msendMessage:%d", penv,
			pContext->weak_this, pContext->msendMessage);
	penv->CallStaticVoidMethod(pContext->clazz, pContext->msendMessage,
			pContext->weak_this, what, arg0, arg1);
}

void* CmdChannelThread(void *param) {
	__android_log_print(ANDROID_LOG_WARN, TAG,
			"i'm %d, and i'm in, pContext:%d", pthread_self(), param);
	CONTEXT *pContext = (CONTEXT *) param;
	//Attach主线程
	JNIEnv *env;
	pContext->g_jvm->AttachCurrentThread(&env, NULL);
	int bufLen = 1024;
	char buf[1024];
	MSG_HEADER *phead = (MSG_HEADER *) buf;
	SOCKET socketfd = pContext->nCmdSocketfd;
	int sendLen = 0;
	int recvOff = 0;
	fd_set read_flags, write_flags; // you know what these are
	while (pContext->isCmdAlive) {
		// Zero the flags ready for using
		FD_ZERO(&read_flags);
		FD_ZERO(&write_flags);
		if (sendLen > 0) {
			FD_SET(socketfd, &write_flags);
		} else {
			FD_SET(socketfd, &read_flags);
		}
		struct timeval waitd;
		waitd.tv_sec = 0; // Make select wait up to 0 second for data
		waitd.tv_usec = 5000; // and 900 milliseconds.
		int err = select(socketfd + 1, &read_flags, &write_flags, NULL, &waitd);
		if (err < 0) {
			__android_log_print(ANDROID_LOG_WARN, TAG,
					"select error: %d, errno: %d", err, errno);
			break;
		} else if (err == 0) {
			continue;
		}

		if (FD_ISSET(socketfd, &read_flags)) { //Socket ready for reading
			FD_CLR(socketfd, &read_flags);
			int requestSize = sizeof(MSG_HEADER);
			int recvdSize = recv(socketfd, buf + recvOff, bufLen - recvOff, 0);
			if (recvdSize < 0) {
				if (errno == EWOULDBLOCK || errno == EINTR) {
					continue;
				} else {
					__android_log_print(ANDROID_LOG_WARN, TAG,
							"recvd len: %d, errno: %d, i'll close this session",
							recvdSize, errno);
					break;
				}
			} else if (recvdSize == 0) {
				__android_log_print(ANDROID_LOG_WARN, TAG,
						"recvd len: 0, i'll close the session");
				break;
			} else {
				recvOff += recvdSize;
				__android_log_print(ANDROID_LOG_INFO, TAG, "recvd len: %d",
						recvdSize);
			}
			// 解析接受到的数据
			if (recvOff >= sizeof(MSG_HEADER)) {
				if (phead->nLength > recvOff) { // 尚未接受完成，继续
					continue;
				}
				sendMessage(pContext, CMD_CMD_MESSAGE, phead->nCmd, 0);
				if (phead->nCmd == ARG_CMD_KEEPALIVE) {
					sendLen = sizeof(MSG_HEADER);
					recvOff = 0;
				} else {
					if (phead->nLength > bufLen) {
						break;
					}
					recvOff -= phead->nLength; // 将请求丢弃即可
					if (recvOff > 0) {
						// 将下一个请求的部分放到开始处
						memcpy(buf, buf + phead->nLength, recvOff); // 无交叠
					}
				}
			}
		} else if (FD_ISSET(socketfd, &write_flags)) { //Socket ready for writing
			FD_CLR(socketfd, &write_flags);
			int sendSize = send(socketfd, buf, sendLen, 0);
			sendMessage(pContext, CMD_CMD_SEND, 0, sendSize);
			__android_log_print(ANDROID_LOG_INFO, TAG, "send len: %d",
					sendSize);
			if (sendSize < 0) {
				__android_log_print(ANDROID_LOG_WARN, TAG,
						"send error, i'll close this session");
				break;
			}
			sendLen -= sendSize;
		} else {
			__android_log_print(ANDROID_LOG_DEBUG, TAG,
					"not readable and writable");
		}
	}
	close(socketfd);
	sendMessage(pContext, CMD_CMD_QUIT, 0, 0);
	__android_log_print(ANDROID_LOG_WARN, TAG,
			"i'm %d, and i'm done!, pContext :%d", pthread_self(), pContext);
	//Detach主线程
	pContext->g_jvm->DetachCurrentThread();
	return 0;
}

void* DataChannelThread(void *param) {

	__android_log_print(ANDROID_LOG_WARN, TAG,
			"i'm %d, and i'm in, pContext:%d", pthread_self(), param);
	CONTEXT *pContext = (CONTEXT *) param;
	//Attach主线程
	JNIEnv *env;
	pContext->g_jvm->AttachCurrentThread(&env, NULL);
	int bufLen = 1024;
	char buf[1024];
	PROXY_HEADER *phead = (PROXY_HEADER *) buf;
	SOCKET socketfd = pContext->nDataSocketfd;
	int recvOff = 0;
	fd_set read_flags, write_flags; // you know what these are
	char *pBufferSend = 0;
	int sendLen = 0, sendPos = 0;
	while (pContext->isDataAlive) {
		// Zero the flags ready for using
		FD_ZERO(&read_flags);
		FD_ZERO(&write_flags);
		if (sendLen == 0) {
			pContext->dataSendBuf.dequeue(pBufferSend, sendLen);
			sendPos = 0;
		} else {
			static int i = 0;
			if (i == 0) {
				__android_log_print(ANDROID_LOG_WARN, TAG,
						"sendLen : %d,sendPos:%d, isAlive:%d, pContext:%d",
						sendLen, sendPos, pContext->isDataAlive, pContext);
				i = 1;
			}
		}
		if (sendLen > 0) {
			FD_SET(socketfd, &write_flags);
		} else {
			FD_SET(socketfd, &read_flags);
		}
		struct timeval waitd;
		waitd.tv_sec = 0; // Make select wait up to 0 second for data
		waitd.tv_usec = 5000; // and 900 milliseconds.
		int err = select(socketfd + 1, &read_flags, &write_flags, NULL, &waitd);
		if (err < 0) {
			__android_log_print(ANDROID_LOG_ERROR, TAG,
					"data select error: %d, errno: %d", err, errno);
			break;
		} else if (err == 0) {
			continue;
		}

		if (FD_ISSET(socketfd, &read_flags)) { //Socket ready for reading
			FD_CLR(socketfd, &read_flags);
			int recvdSize = recv(socketfd, buf + recvOff, bufLen - recvOff, 0);
			if (recvdSize < 0) {
				if (errno == EWOULDBLOCK || errno == EINTR) {
					continue;
				} else {
					__android_log_print(ANDROID_LOG_ERROR, TAG,
							"data recvd len: %d, errno: %d, i'll close this session",
							recvdSize, errno);
					break;
				}
			} else if (recvdSize == 0) {
				__android_log_print(ANDROID_LOG_ERROR, TAG,
						"data recvd len: 0, i'll close the session");
				break;
			} else {
				recvOff += recvdSize;
				__android_log_print(ANDROID_LOG_INFO, TAG, "data recvd len: %d",
						recvdSize);

				// 解析接受到的数据
				if (recvOff >= sizeof(PROXY_HEADER)) {
					if (recvOff < phead->dwLength + 7) { // 尚未接受完成，继续
						continue;
					}
					if (phead->cType == 1) {
						if (phead->dwLength + 7 > bufLen) {
							__android_log_print(ANDROID_LOG_ERROR, TAG, "\n%d",
									__LINE__);
							assert(0);
						}
						char flg = *(buf + sizeof(PROXY_HEADER));
						__android_log_print(ANDROID_LOG_ERROR, TAG,
								"data recvd flg : %d", flg);
						if (flg == 5) { // request
							char cStreamCount =
									*(buf + sizeof(PROXY_HEADER) + 1);
							cStreamCount = 2;
							sendMessage(pContext, CMD_DATA_MESSAGE,
									ARG_DATA_REQUEST_FRAME, cStreamCount);

							char *pTemp = new char[1024];
							PROXY_HEADER *pHeader = (PROXY_HEADER *) pTemp;
							memset(pHeader, 0, sizeof(PROXY_HEADER));
							pHeader->cType = 1;
							pHeader->dwSourceID = pContext->nSourceID;
							pHeader->wSeqNum = 0;
							pHeader->cVersion_StreamID =
									phead->cVersion_StreamID;
							pHeader->cFlag = 16;
							pTemp[sizeof(PROXY_HEADER)] = (char) 0x09;
							pTemp[sizeof(PROXY_HEADER) + 1] = cStreamCount;

							StreamInfoResp *pResp = (StreamInfoResp *) (pTemp
									+ sizeof(PROXY_HEADER) + 2);
							memset(pResp, 0, sizeof(StreamInfoResp));

							pResp->cStreamID = 1;
							pResp->cStreamType = 2;
							pResp->dwSourceID = pContext->nSourceID;

							VIDEOFORMAT *pFormat = (VIDEOFORMAT *) pResp->format;

							pFormat->wLength = sizeof(VIDEOFORMAT);
							pFormat->cFps = 20; // fps
							pFormat->bmpHeader.biSize =
									sizeof(BITMAPINFOHEADER);
							pFormat->bmpHeader.biWidth = pContext->videoSize[0];
							pFormat->bmpHeader.biHeight =
									pContext->videoSize[1];
							pFormat->bmpHeader.biPlanes = 1;
							pFormat->bmpHeader.biBitCount = 24;
							pFormat->bmpHeader.biCompression = 0x34363248;
							pFormat->bmpHeader.biSizeImage = 0;
							pFormat->bmpHeader.biXPelsPerMeter = 0;
							pFormat->bmpHeader.biYPelsPerMeter = 0;
							pFormat->bmpHeader.biClrUsed = 0;
							pFormat->bmpHeader.biClrImportant = 0;
							pFormat->formatValid = 0;
							strcpy(pFormat->VideoName, "ANDROID");
							pResp->temp[13] = 6;

							pResp = (StreamInfoResp *) (pTemp
									+ sizeof(PROXY_HEADER) + 2
									+ sizeof(StreamInfoResp));
							memset(pResp, 0, sizeof(StreamInfoResp));

							pResp->cStreamID = 2;
							pResp->cStreamType = 3;
							pResp->dwSourceID = pContext->nSourceID;
							pResp->temp[13] = 6;
							AUDIOFORMAT *pAFormat =
									(AUDIOFORMAT *) pResp->format;
							/*
							 * typedef struct{ //在MSDN中有说明，用于解码音频
							 short wFormatTag;	//=0x7104（AAC）
							 short nChannels;		//=2
							 int  nSamplesPerSec;	//=采样率
							 int  nAvgBytesPerSec;	//=采样率*2*2
							 short nBlockAlign;	//16
							 short wBitsPerSample;//2
							 short cbSize;
							 } WAVEFORMATEX;
							 typedef struct audio_format{
							 u_int16	wLength; 	//格式块的长度, = sizeof( AUDIOFORMAT)
							 WAVEFORMATEX	waveFormat; //音频流格式，扩展格式数据在AudioFormat后
							 } AUDIOFORMAT;
							 * */

							pAFormat->wLength = sizeof(AUDIOFORMAT);
							pAFormat->waveFormat.wFormatTag = 0x7104; // fps
							pAFormat->waveFormat.nChannels = 2;
							pAFormat->waveFormat.nSamplesPerSec = 32000;
							pAFormat->waveFormat.nAvgBytesPerSec = 32000 * 4;
							pAFormat->waveFormat.nBlockAlign = 1;
							pAFormat->waveFormat.wBitsPerSample = 16;
							pAFormat->waveFormat.cbSize = 0;

//							pAFormat->wLength = sizeof(AUDIOFORMAT);
//							pAFormat->waveFormat.wFormatTag = 0x7104; // fps
//							pAFormat->waveFormat.nChannels = 2;
//							pAFormat->waveFormat.nSamplesPerSec = 32000;
//							pAFormat->waveFormat.nAvgBytesPerSec = 32000 * 4;
//							pAFormat->waveFormat.nBlockAlign = 1;
//							pAFormat->waveFormat.wBitsPerSample = 16;
//							pAFormat->waveFormat.cbSize = 0;

//							pAFormat->wLength = sizeof(AUDIOFORMAT);
//							pAFormat->waveFormat.wFormatTag = 1; // fps
//							pAFormat->waveFormat.nChannels = 2;
//							pAFormat->waveFormat.nSamplesPerSec = 32000;
//							pAFormat->waveFormat.nAvgBytesPerSec = 32000 * 4;
//							pAFormat->waveFormat.nBlockAlign = 4;
//							pAFormat->waveFormat.wBitsPerSample = 16;
//							pAFormat->waveFormat.cbSize = 0;

							int nLength = sizeof(StreamInfoResp) * cStreamCount
									+ 2 + sizeof(PROXY_HEADER);
							pHeader->dwLength = nLength - 7; // 去除7个字节
							int result = 0;
							while (result == 0) {
								result = pContext->dataSendBuf.queue(pTemp,
										nLength);
								if (result == 0) {
									char *pAbort = NULL;
									int len = 0;
									pContext->dataSendBuf.dequeue(pAbort, len);
									if (pAbort != NULL) {
										delete pAbort;
									}
									__android_log_print(ANDROID_LOG_INFO, TAG,
											"queue error!");
								}
							}
						} else if (flg == 0x0D) { // key frm
							sendMessage(pContext, CMD_DATA_MESSAGE, flg, 0);
						} else if (flg == 0x5B) { //1.1.8. ConnectClose
							sendMessage(pContext, CMD_DATA_MESSAGE, flg, 0);
						} else if (flg == 0x29) { // heart beat
							const int nLength = phead->dwLength + 7;
							char *pTemp = new char[nLength];
							memcpy(pTemp, buf, nLength);

							int result = 0;
							while (result == 0) {
								result = pContext->dataSendBuf.queue(pTemp,
										nLength);
								if (result == 0) {
									char *pAbort = NULL;
									int len = 0;
									pContext->dataSendBuf.dequeue(pAbort, len);
									if (pAbort != NULL) {
										delete pAbort;
									}
									__android_log_print(ANDROID_LOG_INFO, TAG,
											"heart beat .queue error!");
								}
							}

							sendMessage(pContext, CMD_DATA_MESSAGE, flg, 0);
						} else {
							sendMessage(pContext, CMD_DATA_MESSAGE, flg,
									phead->dwLength);
						}
						recvOff -= phead->dwLength + 7;
						if (recvOff > 0) {
							// 将下一个请求的部分放到开始处
							memcpy(buf, buf + phead->dwLength + 7, recvOff); // 无交叠
						}

					} else {
						__android_log_print(ANDROID_LOG_INFO, TAG,
								"head->cType: %d", phead->cType);
					}
				}
			}

		} else if (FD_ISSET(socketfd, &write_flags)) { //Socket ready for writing
			FD_CLR(socketfd, &write_flags);
//			if (sendPos == 0) {
//				static int tm = 0;
//				PROXY_HEADER *pheader = (PROXY_HEADER *) pBufferSend;
//				if (pheader->dwPlayStamp < tm) {
//					__android_log_print(ANDROID_LOG_ERROR, TAG,
//							"\n%d,tm:%d,playStamp:%d", __LINE__, tm,
//							pheader->dwPlayStamp);
//					assert(0);
//				}
//				tm = pheader->dwPlayStamp;
//			}
			int sendSize = send(socketfd, pBufferSend + sendPos, sendLen, 0);
			if (sendSize < 0) {
				__android_log_print(ANDROID_LOG_ERROR, TAG,
						"data send error %d, i'll close this session",
						sendSize);
				break;
			}
			sendLen -= sendSize;
			sendPos += sendSize;
			if (sendLen == 0) {
				delete pBufferSend;
				pBufferSend = NULL;
			}
		} else {
			__android_log_print(ANDROID_LOG_ERROR, TAG,
					"data not readable and writable");
		}
	}
	close(socketfd);
	sendMessage(pContext, CMD_DATA_QUIT, 0, 0);
	__android_log_print(ANDROID_LOG_WARN, TAG, "i'm %d, and i'm done!",
			pthread_self());
	pContext->g_jvm->DetachCurrentThread();
	return NULL;
}

int reg2Server(CONTEXT *pContext, const char *strAddr, int port,
		const char *pUsr, const char *pPwd) {
	CONN_REQ *pconn = NULL;
	CONN_RESP *pconn_resp = NULL;
	char buf[4096];
	pconn = (CONN_REQ *) buf;
	pconn_resp = (CONN_RESP *) buf;
	struct hostent *host;
	struct sockaddr_in serv_addr;
	if ((host = gethostbyname(strAddr)) == NULL) {
		return -1;
	}
	SOCKET socketfd = socket(PF_INET, SOCK_STREAM, 0);
	if (socketfd == -1) {
		return -2;
	}
#define doreturn(a) close(socketfd);\
	return a;
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(port);
	serv_addr.sin_addr = *((struct in_addr *) host->h_addr);
	bzero(&(serv_addr.sin_zero), 8);
	if (connect(socketfd, (struct sockaddr *) &serv_addr,
			sizeof(struct sockaddr)) == -1) {
		doreturn(-3);
	}
	pconn->hdr.nCmd = MSG_CONNECT_REQUEST;
	pconn->hdr.nLength = sizeof(MSG_HEADER) + sizeof(PRODUCER_INFO);
	pconn->ID = 4;
	strcpy(pconn->pi.ProducerName, pUsr);
	strcpy(pconn->pi.sPassword, pPwd);
	pconn->pi.bMulticast = 0;
	pconn->pi.nCapacity = 0;
	pconn->pi.nManufacturer = IPHONE_IPCAMERA;
	pconn->pi.nReserved = inet_addr("127.0.0.1");
	int len = MAX_NAME_LENGTH;
	char pwd[MAX_NAME_LENGTH] = { 0 };
	EncrytString(pwd, MAX_NAME_LENGTH, pconn->pi.sPassword, len);
	if (send(socketfd, (char*) pconn, pconn->hdr.nLength, 0)
			!= pconn->hdr.nLength) {
		doreturn(-4);
	}
	int requestSize = sizeof(MSG_HEADER) + sizeof(PRODUCER_RESP);
	int recvdSize = recv(socketfd, (char*) pconn_resp, requestSize, 0);
	if (requestSize != recvdSize) {
		doreturn(-5);
	}
	if (MSG_CONNECT_RESPONSE != pconn_resp->hdr.nCmd) {
		doreturn(0x1000);
	}
	if (0 != pconn_resp->pi.nResult) {
		int ret = 0x1000 + pconn_resp->pi.nResult;
		doreturn(ret);
	}
	CHANNEL_NOTIFY *pchannel = (CHANNEL_NOTIFY *) buf;
	AV_CHANNEL_INFO *pavchn = (AV_CHANNEL_INFO *) (buf + sizeof(CHANNEL_NOTIFY));
	pchannel->hdr.nCmd = MSG_CHANNEL_INFO_NOTIFY;
	pchannel->hdr.nLength = sizeof(CHANNEL_NOTIFY) + sizeof(AV_CHANNEL_INFO);
	pchannel->nType = 1;
	pchannel->nCount = 1;
	pchannel->nReserved = 0;
	memset(pavchn, 0, sizeof(AV_CHANNEL_INFO));
	pavchn->bValid = 1;
	strcpy(pavchn->ChannelName, pUsr);
	pavchn->nMode = 2;
	pavchn->bMulticast = 0;
	pavchn->bVideo = 1;
	pavchn->bAudio = 0;
	pavchn->bCradle = 1;
	if (send(socketfd, (char*) pchannel, pchannel->hdr.nLength, 0)
			!= pchannel->hdr.nLength) {
		doreturn(-6);
	}
	pchannel->hdr.nCmd = MSG_CHANNEL_INFO_NOTIFY;
	pchannel->hdr.nLength = sizeof(CHANNEL_NOTIFY);
	pchannel->nType = 0;
	pchannel->nCount = 0;
	pchannel->nReserved = 0;
	if (send(socketfd, (char*) pchannel, pchannel->hdr.nLength, 0)
			!= pchannel->hdr.nLength) {
		doreturn(-7);
	}
	requestSize = sizeof(CHANNEL_NOTIFY) + sizeof(AV_CHANNEL_INFO);
	recvdSize = recv(socketfd, (char*) pchannel, requestSize, 0);
	if (recvdSize != requestSize) {
		doreturn(-8);
	}
	if (pchannel->hdr.nCmd != MSG_CHANNEL_INFO_NOTIFY
			|| pchannel->hdr.nLength != requestSize) {
		doreturn(-9);
	}
	if (pchannel->nCount != 1 || pchannel->nType != TYPE_CHANNEL_NOTIFY_AV) {
		doreturn(-10);
	}

	if ((host = gethostbyname(pavchn->IPAddr)) == NULL) {
		doreturn(-11);
	}

	SOCKET dataSocketfd = socket(PF_INET, SOCK_STREAM, 0);
	if (dataSocketfd == -1) {
		doreturn(-12);
	}
	serv_addr.sin_family = AF_INET;
	serv_addr.sin_port = htons(pavchn->nPort);
	serv_addr.sin_addr = *((struct in_addr *) host->h_addr);
	bzero(&(serv_addr.sin_zero), 8);
	if (connect(dataSocketfd, (struct sockaddr *) &serv_addr,
			sizeof(struct sockaddr)) == -1) {
		doreturn(-13);
	}
	fcntl(socketfd, F_SETFL, O_NONBLOCK);
	pContext->nCmdSocketfd = socketfd;
	pContext->nDataSocketfd = dataSocketfd;
	strcpy(pContext->szDataIP, pavchn->IPAddr);
	pContext->nDataPort = pavchn->nPort;
	pContext->nSourceID = pavchn->nSourceID;

	pContext->isCmdAlive = true;
	/* Create a new thread. The new thread will run the print_xs
	 function. */
	if (pthread_create(&pContext->tCmd, NULL, &CmdChannelThread, pContext)
			!= 0) {
		pContext->isCmdAlive = false;
		doreturn(-14);
	}
	pContext->isDataAlive = true;
	if (pthread_create(&pContext->tData, NULL, &DataChannelThread, pContext)
			!= 0) {
		pContext->isCmdAlive = false;
		doreturn(-15);
	}
	return 0;
}
JNIEXPORT jint JNICALL Java_com_john_xstream_XStream_native_1setup(JNIEnv *penv,
		jobject thiz, jobject weak_thiz) {
	CONTEXT *pContext = new CONTEXT;
	penv->GetJavaVM(&pContext->g_jvm);
	jclass clazz = penv->GetObjectClass(thiz);
	if (clazz != NULL) {
		pContext->clazz = (jclass) penv->NewGlobalRef(clazz);
		pContext->weak_this = penv->NewGlobalRef(weak_thiz);
		pContext->msendMessage = penv->GetStaticMethodID(clazz,
				"sendMessageFromNative", "(Ljava/lang/Object;III)V");
	}
	return (jint) pContext;
}

JNIEXPORT jint JNICALL Java_com_john_xstream_XStream_reg2Server(JNIEnv * pEnv,
		jobject jObj, jint handle, jstring addr, jint port, jstring usr,
		jstring pwd) {
	CONTEXT *pContext = (CONTEXT *) handle;
	const char *pAddr = pEnv->GetStringUTFChars(addr, 0);
	const char *pUser = pEnv->GetStringUTFChars(usr, 0);
	const char *pPwd = pEnv->GetStringUTFChars(pwd, 0);
	int result = reg2Server(pContext, pAddr, port, pUser, pPwd);
	pEnv->ReleaseStringUTFChars(addr, pAddr);
	pEnv->ReleaseStringUTFChars(usr, pUser);
	pEnv->ReleaseStringUTFChars(pwd, pPwd);
	return result;
}

void setIntParam(int handle, int key, int value) {
	if (handle == 0)
		return;
	CONTEXT *pContext = (CONTEXT *) handle;
	if (key == KEY_VIDEO_WIDTH) {
		pContext->videoSize[0] = value;
	} else if (key == KEY_VIDEO_HEIGHT) {
		pContext->videoSize[1] = value;
	}
}

JNIEXPORT void JNICALL Java_com_john_xstream_XStream_setIntParam(JNIEnv *penv,
		jobject jobj, jint handle, jintArray jiaParams) {
	jsize paramLen = penv->GetArrayLength(jiaParams);
	if (paramLen % 2 != 0) {
		return;
	}
	jint *pParams = penv->GetIntArrayElements(jiaParams, 0);
	for (int i = 0; i < paramLen;) {
		int key = *(pParams + i++);
		int value = *(pParams + i++);
		setIntParam(handle, key, value);
	}
	penv->ReleaseIntArrayElements(jiaParams, pParams, 0);
}

/*
 * Class:     com_john_xstream_XStream
 * Method:    pumpVideoFrame
 * Signature: (I[B[I)I
 */JNIEXPORT jint JNICALL Java_com_john_xstream_XStream_pumpFrame(JNIEnv *penv,
		jobject jobj, jint handle, jbyteArray jbaFrame, jintArray jiaParams) {
	CONTEXT *pContext = (CONTEXT *) handle;
	jsize paramLen = penv->GetArrayLength(jiaParams);
	if (paramLen < 8) {
		return -2;
	}
	jint *pParams = penv->GetIntArrayElements(jiaParams, 0);
//	 frame.type,
	int type = *pParams;
	int offset = *(pParams + 1);
	int length = *(pParams + 2);
	int keyFrm = *(pParams + 3);
	int tmStamp = *(pParams + 4);
	int nIdx = *(pParams + 5);
	int width = *(pParams + 6);
	int height = *(pParams + 7);
	/*
	 * PROXY_HEADER *pheader =
	 (PROXY_HEADER *) pContext->dataSendBuf.pDataSendBuffer;
	 pheader->cType = 2;
	 pheader->dwSourceID = pContext->nSourceID;
	 pheader->wSeqNum = nIdx;
	 pheader->cVersion_StreamID = 0x05;
	 pheader->dwLength = sizeof(PROXY_HEADER) - 7 + length;
	 pheader->cFlag = keyFrm == 1 ? 10 : 12; //cFlags: 当前数据包的包类型，10表示I帧，12表示P帧，I帧表示一个编码的开始，用于解码时进行第一帧初始化。
	 pheader->dwPlayStamp = tmStamp;
	 jbyte *pData = penv->GetByteArrayElements(jbaFrame, 0);
	 memcpy(pContext->dataSendBuf.pDataSendBuffer + sizeof(PROXY_HEADER),
	 pData + offset, length);
	 * */
	if (offset < sizeof(PROXY_HEADER)) {
		__android_log_print(ANDROID_LOG_ERROR, TAG, "\n%d", __LINE__);
		assert(0);
	}

	offset -= sizeof(PROXY_HEADER);
	length += sizeof(PROXY_HEADER);

	char *pData = (char *) penv->GetByteArrayElements(jbaFrame, 0);
	char *pBuffer = new char[length];
	memcpy(pBuffer, (pData + offset), length);

	PROXY_HEADER *pheader = (PROXY_HEADER *) pBuffer;

	pheader->dwSourceID = pContext->nSourceID;
	pheader->wSeqNum = nIdx;
	pheader->cVersion_StreamID = 0x05;
	pheader->dwLength = length - 7;

//  public static final byte FRAME_TYPE_VIDEO = 1;

// Field descriptor #13 B
//	  public static final byte FRAME_TYPE_AUDIO = 2;
	if (type == 1) {
		pheader->cType = 2;
		pheader->cFlag = keyFrm == 1 ? 10 : 12; //cFlags: 当前数据包的包类型，10表示I帧，12表示P帧，I帧表示一个编码的开始，用于解码时进行第一帧初始化。
	} else {
		pheader->cType = 3;
		pheader->cFlag = 17; //cFlags: 当前数据包的包类型，恒为17，表示一个音频关键包，暂时所有音频的数据包类型全部为17。
	}

	pheader->dwPlayStamp = tmStamp;

//	memcpy(pContext->dataSendBuf.pDataSendBuffer + sizeof(PROXY_HEADER),
//			pData + offset, length);
	int result = pContext->dataSendBuf.queue(pBuffer, length);
	if (result == 0) {
		delete pBuffer;
	}

	penv->ReleaseByteArrayElements(jbaFrame, (jbyte *) pData, 0);
	penv->ReleaseIntArrayElements(jiaParams, pParams, 0);
	return result == 0 ? -1 : 0;
}

void closeCmdChannel(int handle) {
	CONTEXT *pContext = (CONTEXT *) handle;
	if (pContext->tCmd != 0) {
		pthread_join(pContext->tCmd, 0);
		pContext->tCmd = 0;
		__android_log_print(ANDROID_LOG_WARN, TAG, "pthread_join tCmd");

	}
	if (pContext->nCmdSocketfd != 0) {
		close(pContext->nCmdSocketfd);
		pContext->nCmdSocketfd = 0;
		__android_log_print(ANDROID_LOG_WARN, TAG, "close nCmdSocketfd");
	}
}

void closeDataChannel(int handle) {
	CONTEXT *pContext = (CONTEXT *) handle;
	if (pContext->tData != 0) {
		pthread_join(pContext->tData, 0);
		pContext->tData = 0;
		__android_log_print(ANDROID_LOG_WARN, TAG, "pthread_join tData");
	}
	if (pContext->nDataSocketfd != 0) {
		close(pContext->nDataSocketfd);
		pContext->nDataSocketfd = 0;
		__android_log_print(ANDROID_LOG_WARN, TAG, "close nDataSocketfd");
	}
}

JNIEXPORT void JNICALL Java_com_john_xstream_XStream_unreg(JNIEnv *penv,
		jobject jobj, jint handle) {
	CONTEXT *pContext = (CONTEXT *) handle;
	__android_log_print(ANDROID_LOG_WARN, TAG, "unreg begin");

	pContext->isCmdAlive = false;
	closeCmdChannel(handle);
	pContext->isDataAlive = false;
	closeDataChannel(handle);

	penv->DeleteGlobalRef(pContext->clazz);
	penv->DeleteGlobalRef(pContext->weak_this);
	delete pContext;
	__android_log_print(ANDROID_LOG_WARN, TAG, "unreg end");
}

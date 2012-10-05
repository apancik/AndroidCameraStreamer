//Client
#include <iostream>
#include <fstream>
#include <boost/array.hpp>
#include <boost/asio.hpp>
#include "opencv2/opencv.hpp"
#include "datapacket.pb.h"
#include <boost/lexical_cast.hpp>
#include <boost/algorithm/string.hpp>
#include <boost/shared_ptr.hpp>
#include "datapacket.pb.h"
#include <sstream>;
#include <boost/thread/thread.hpp>


using boost::asio::ip::tcp;

enum Source {
    WEBCAM = 1,
    VIDEO_FILE = 2,
    RECORDING = 3
};

void help() {
	std::cout << "Video Streamer" << std::endl;
	std::cout << "Copyright 2011-2012 Andrej Pancik" << std::endl << std::endl;

	std::cout << "Usage: videostreamer [options]" << std::endl;
	std::cout << "Options:" << std::endl;
	std::cout << "    -src [webcam/file/recording]  Data source" << std::endl;
	std::cout << "    -f --filename [filename]      Filename for data source" << std::endl;
	std::cout << "    -h --host [hostname]          Host" << std::endl;
	std::cout << "    -p --port [portnumber]        Port" << std::endl;
}

int main(int argc, char* argv[]) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

    // LOAD SETTINGS

    bool verbose = false;

    std::string host = "localhost";
    std::string port = "1313";

    std::string filename = "recording.bin";

    Source source = WEBCAM;

	if(argc == 1) {
		help();
		std::cout << std::endl << "Using default settings" << std::endl;
	}

    for (int i = 0; i < argc; i++) {
        std::string param(argv[i]);

        if ((param.compare("-src") == 0) || (param.compare("--source") == 0)) {
            i++;
            std::string sourceDevice(argv[i]);

            boost::algorithm::to_lower(sourceDevice);

            if (sourceDevice.compare("webcam") == 0) {
                source = WEBCAM;
            } else if (sourceDevice.compare("file") == 0) {
                source = VIDEO_FILE;
            } else if (sourceDevice.compare("recording") == 0) {
                source = RECORDING;
            }
        } else if ((param.compare("-p") == 0) || (param.compare("--port") == 0)) {
            port = std::string(argv[++i]);
        } else if ((param.compare("-h") == 0) || (param.compare("--host") == 0)) {
            host = std::string(argv[++i]);
        } else if ((param.compare("-f") == 0) || (param.compare("--filename") == 0)) {
            filename = std::string(argv[++i]);
        }
    }


    std::vector<int> param = std::vector<int>(2);
    param[0] = CV_IMWRITE_JPEG_QUALITY;
    param[1] = 95; //TODO Constant

    int counter = 0;

    std::cout << "Connecting to " << host << " " << port << std::endl;

    // SOCKET SETUP
    boost::asio::io_service io_service;

    tcp::resolver resolver(io_service);
    tcp::resolver::query query(host, port);
    tcp::resolver::iterator endpoint_iterator = resolver.resolve(query);

    tcp::socket socket(io_service);
    boost::asio::connect(socket, endpoint_iterator);

    // VIDEO SOURCE INITIALISATION

    cv::VideoCapture videoCapture;
    boost::shared_ptr<std::ifstream> recordingFile;
    switch (source) {
        case RECORDING:
            recordingFile.reset(new std::ifstream(filename.data(), std::ios::in | std::ios::binary));
            break;
        case VIDEO_FILE:
            videoCapture = cv::VideoCapture(filename);
            if (!videoCapture.isOpened())
                return 1;
            break;
        case WEBCAM:
        default:
            videoCapture = cv::VideoCapture(-1);
            if (!videoCapture.isOpened())
                return 1;
            break;
    }
    //double rate = capture.get(CV_CAP_PROP_FPS);

    // MAIN BROADCASTING LOOP

    do {
        int networkImageSize;
        int networkHeaderSize;
        std::vector<uchar> headerBuffer; //buffer for coding
        std::vector<uchar> imageBuffer; //buffer for coding

        switch (source) {
            case VIDEO_FILE:
            case WEBCAM:
            {
                cv::Mat image;

                if (!videoCapture.read(image)) {
                    std::cout << "Error loading pic" << std::endl;
                    break;
                }

                cv::imencode(".jpg", image, imageBuffer, param);
                networkImageSize = htonl(imageBuffer.size());

                camerastreamer::Header header;
                //header.SerializeToArray(headerBuffer); // TODO
                //header.s
                std::ostringstream oss; //TODO Make it pretty
                header.SerializeToOstream(&oss);
                std::string output = oss.str();
                headerBuffer.resize(output.size());
                headerBuffer = std::vector<uchar > (output.begin(), output.end());
                networkHeaderSize = htonl(headerBuffer.size());
            }
                break;
            case RECORDING:
            {
                boost::this_thread::sleep(boost::posix_time::milliseconds(500)); //TODO konstanta vyviest von
                (*recordingFile) >> networkHeaderSize;
                int headerSize = ntohl(networkHeaderSize);

                std::cout << "Loading frame from file..." << std::endl << "Header size " << headerSize << std::endl;

                headerBuffer.resize(headerSize);
                recordingFile->read((char*) headerBuffer.data(), headerSize);

                (*recordingFile) >> networkImageSize;
                int imageSize = ntohl(networkImageSize);

                std::cout << "Image size " << imageSize << std::endl;

                imageBuffer.resize(imageSize);
                recordingFile->read((char*) imageBuffer.data(), imageSize);
            }
                break;
        }

        boost::system::error_code error;

        boost::asio::write(socket, boost::asio::buffer((char*) &networkHeaderSize, sizeof (networkHeaderSize)), error);
        boost::asio::write(socket, boost::asio::buffer(headerBuffer), error);

        boost::asio::write(socket, boost::asio::buffer((char*) &networkImageSize, sizeof (networkImageSize)), error);
        boost::asio::write(socket, boost::asio::buffer(imageBuffer), error);

        std::cout << "Sending image of size " << imageBuffer.size() << std::endl;
    } while (true);

    switch (source) {
        case RECORDING:
            recordingFile->close();
        case WEBCAM:
        case VIDEO_FILE:
        default:
            videoCapture.release();
            break;
    }


    return 0;
}
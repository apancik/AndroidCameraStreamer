#include <iostream>
#include <string>
#include <stdlib.h>

#include "video_host.h"

#include "opencv2/opencv.hpp"

#include <boost/asio.hpp>
#include <boost/shared_ptr.hpp>

using boost::asio::ip::tcp;

void help() {
	std::cout << "Video Host" << std::endl;
	std::cout << "Copyright 2011-2012 Andrej Pancik" << std::endl << std::endl;

	std::cout << "Usage: videohost [options]" << std::endl;
	std::cout << "Options:" << std::endl;
	std::cout << "    -r --record                   Enable recording" << std::endl;
	std::cout << "    -v --verbose                  Verbose Mode" << std::endl;
	std::cout << "    -f --filename [filename]      Host" << std::endl;
	std::cout << "    -p --port [portnumber]        Port" << std::endl;
	std::cout << "    -h --hide                     Hide preview" << std::endl;
	std::cout << "    -c --canny                    Process frame with canny edge detection" << std::endl;
}

int main(int argc, char *argv[]) {
    GOOGLE_PROTOBUF_VERIFY_VERSION;

	if(argc == 1) {
		help();
		std::cout << std::endl << "Using default settings" << std::endl;
	}

    // LOAD SETTINGS

    bool verbose = false;
    bool record = false;
    bool display = true;
	bool canny = false;
	bool features = true;

    int portNumber = 1313;

    std::string filename = "recording.bin";

    for (int i = 0; i < argc; i++) {
        std::string param(argv[i]);

        if ((param.compare("-r") == 0) || (param.compare("--record") == 0)) {
            record = true;
        } else if ((param.compare("-h") == 0) || (param.compare("--hide") == 0)) {
            display = false;
        } else if ((param.compare("-v") == 0) || (param.compare("--verbose") == 0)) {
            verbose = true;
        } else if ((param.compare("-c") == 0) || (param.compare("--canny") == 0)) {
	        canny = true;
			cv::namedWindow("Canny");
			std::cout << "Edge detection enabled" << std::endl;
	    } else if ((param.compare("-p") == 0) || (param.compare("--port") == 0)) {
            std::string port(argv[++i]);
            portNumber = boost::lexical_cast<int>(port);
        } else if ((param.compare("-f") == 0) || (param.compare("--filename") == 0)) {
            filename = std::string(argv[++i]);
        }
    }

    // MAIN LISTENING LOOP

	cv::namedWindow("Preview");										
														
    boost::shared_ptr<VideoCaptureServer> server;

	int64 begin = 0; 
	int step = 10;
	int counter = 0;

    while (true) {
        server.reset(new VideoCaptureServer(portNumber, record, filename));
        try {
            server->waitForConnection();

            std::ofstream recording(filename.data(), std::ios::out | std::ios::binary); //TODO pridat ze podla datumu a casu prida

            while (true) {
				// GRABBING
	
                Frame frame = server->grabFrame();

				if(canny) {
					cv::Mat afterCanny;
					cvtColor(frame.image, afterCanny, CV_RGB2GRAY);
					cv::Canny(afterCanny, afterCanny, 80, 100, 3 );
                    cv::imshow("Canny", afterCanny);
				}
				
				if(features)
					for(int i = 0; i < frame.features.size(); i++)
						cv::circle(frame.image, frame.features[i], 2, cv::Scalar(0, 0, 255), -1, 8, 0 );

                if (display)
                    cv::imshow("Preview", frame.image);

                if (cv::waitKey(1) > 0)
                    break;
				
				if(counter++ % step == 0) {
					std::cout << cv::getTickFrequency() * 10 / (cv::getTickCount() - begin) << "FPS" << std::endl;
					begin = cv::getTickCount();
				}
            }
	
        } catch (std::exception &e) {
            std::cerr << e.what() << std::endl;
        }

        server->disconnect();
    }

    google::protobuf::ShutdownProtobufLibrary();

    return 0;
}
/*
 * File:   video_host.h
 * Author: apancik
 *
 * Created on March 25, 2012, 3:33 PM
 */

#ifndef VIDEO_HOST_H
#define	VIDEO_HOST_H

#include <iostream>
#include <fstream>
#include <string>

#include "opencv2/opencv.hpp"

#include <boost/asio.hpp>
#include <boost/date_time/posix_time/posix_time.hpp>
#include <boost/scoped_ptr.hpp>

#include "datapacket.pb.h"

using boost::asio::ip::tcp;

struct Frame {
    cv::Mat image;
    std::vector<uchar> encodedImage;
    std::vector<cv::Point> features;

    float accelerometer_0;
    float accelerometer_1;
    float accelerometer_2;

    double latitude;
    double longitude;
};

class VideoCaptureServer {
public:
    VideoCaptureServer(int port, bool record = false, std::string fileName = "recording.bin");
    ~VideoCaptureServer();
    void disconnect();
    void waitForConnection();
    Frame grabFrame();
private:
    void checkConnection(boost::system::error_code error);
    bool recording;
    boost::scoped_ptr<tcp::socket> socket;
    boost::scoped_ptr<boost::asio::io_service> io_service;
    boost::scoped_ptr<tcp::acceptor> acceptor;
    boost::scoped_ptr<std::ofstream> recorder;
};

VideoCaptureServer::VideoCaptureServer(int port, bool record, std::string fileName) {
    io_service.reset(new boost::asio::io_service());
    socket.reset(new tcp::socket(*io_service));
    acceptor.reset(new tcp::acceptor(*io_service, tcp::endpoint(tcp::v4(), port)));
    recording = record;

    if(recording) {
        recorder.reset(new std::ofstream (fileName.data(), std::ios::out | std::ios::binary));
        std::cout << "Initialising recording to " << fileName << std::endl;
    }

    std::cout << "Creating server at port " << port << std::endl;
}

VideoCaptureServer::~VideoCaptureServer() {
    if(recording) {
        recorder->close();
    }
}

void VideoCaptureServer::waitForConnection() {
    std::cout << "Waiting for connection... " << std::endl;

    acceptor->accept(*socket);

    std::cout << "Server started..." << std::endl;
}

void VideoCaptureServer::disconnect() {
    socket.reset();
    acceptor.reset();
    io_service.reset();
}

void VideoCaptureServer::checkConnection(boost::system::error_code error) {
     if (error == boost::asio::error::eof) {
        std::cout << "Connection closed cleanly by peer" << std::endl;
        throw std::runtime_error("Connection closed");
    } else if (error)
        throw boost::system::system_error(error); // Some other error.
}

Frame VideoCaptureServer::grabFrame() {
    Frame frame;

    boost::system::error_code error;

    int networkHeaderSize;
    boost::asio::read(*socket, boost::asio::buffer((char*) &networkHeaderSize, sizeof (networkHeaderSize)), error);

    checkConnection(error);

    int headerSize = ntohl(networkHeaderSize); //convert Int to host endianess
    //std::cout << "Header size " << headerSize << std::endl;

    std::vector<uchar> headerBuffer; //buffer for coding
    headerBuffer.resize(headerSize);

    boost::asio::read(*socket, boost::asio::buffer(headerBuffer, headerSize), error);

    checkConnection(error);

    camerastreamer::Header header;

    if(!header.ParseFromArray(headerBuffer.data(), headerBuffer.size())){
        std::cout << "Error parsing the header" << headerSize << std::endl;
    }

	if(header.has_latitude() && header.has_longitude()) {
		std::cout << "Location long:" << header.latitude() << " lat:" << header.longitude() << std::endl;
	}

    frame.latitude = header.latitude();
    frame.longitude = header.longitude();

    frame.accelerometer_0 = header.acc0();
    frame.accelerometer_1 = header.acc1();
    frame.accelerometer_2 = header.acc2();

    for (int j = 0; j < header.feature_size(); j++) {
        const camerastreamer::PointData& pointData = header.feature(j);

        cv::Point pt =  cv::Point(pointData.x(), pointData.y());

        // DEBUG std::cout << "Feature x:" << pointData.x() << " y:" << pointData.y() << std::endl;

        frame.features.push_back(pt);
    }

	//std::cout << "Acc" << frame.accelerometer_0 << " " << frame.accelerometer_1 << " " << frame.accelerometer_2 << std::endl;

    int networkImageSize;
    boost::asio::read(*socket, boost::asio::buffer((char*) &networkImageSize, sizeof (networkImageSize)), error);

    checkConnection(error);

    int imageSize = ntohl(networkImageSize); //convert Int to host endianess

    std::vector<uchar> imgBuffer; //buffer for coding
    imgBuffer.resize(imageSize);

    boost::asio::read(*socket, boost::asio::buffer(imgBuffer, imageSize), error);

    //std::cout << "Real buffer size " << imgBuffer.size() << std::endl;

    checkConnection(error);

    frame.encodedImage = imgBuffer;
    if(imageSize > 0)
        frame.image = cv::imdecode(cv::Mat(imgBuffer), CV_LOAD_IMAGE_COLOR);
    else
        frame.image = cv::Mat();

    if(recording) {
        (*recorder) << networkHeaderSize;
        recorder->write((char*) headerBuffer.data(), headerSize);
        (*recorder) << networkImageSize;
        recorder->write((char*) imgBuffer.data(), imageSize);
    }


   // frame.image = cv::Mat::zeros(720, 1280, CV_8UC3);

    return frame;
}


#endif	/* VIDEO_HOST_H */


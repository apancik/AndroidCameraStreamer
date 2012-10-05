#!/bin/sh
echo Preparing Includes

#Edit this to compile

OPENCV_PATH=/Users/apancik/oxford/cpp/OpenCV-2.3.1

# =========================== #
# DO NOT EDIT BELOW THIS SIGN #
# =========================== #

echo Generating Protocol Buffers...
protoc datapacket.proto --java_out=android/src --cpp_out=include

echo Building Video Host...
g++ -I./include -I$OPENCV_PATH/include ./cpp/video_host.cpp ./include/video_host.h ./include/datapacket.pb.cc -o videohost -lopencv_core -lopencv_imgproc -lopencv_calib3d -lopencv_video -lopencv_features2d -lopencv_ml -lopencv_highgui -lopencv_objdetect -lopencv_contrib -lopencv_legacy  -lboost_thread -lboost_system -lprotobuf
chmod u+x videohost

echo Building Video Streamer...
g++ -I./include -I$OPENCV_PATH/include ./cpp/video_streamer.cpp ./include/datapacket.pb.cc -o videostreamer -lopencv_core -lopencv_imgproc -lopencv_calib3d -lopencv_video -lopencv_features2d -lopencv_ml -lopencv_highgui -lopencv_objdetect -lopencv_contrib -lopencv_legacy  -lboost_thread -lboost_system -lprotobuf
chmod u+x videostreamer

echo Done.
% SET CORRECT PATHS
LOCAL_PATH = '/usr/local/include'; 
OPENCV_PATH = '/Users/apancik/oxford/cpp/OpenCV-2.3.1';

% DO NOT EDIT BELOW
LIBS = ' -lprotobuf -lboost_thread -lboost_system -lopencv_core -lopencv_imgproc -lopencv_calib3d -lopencv_video -lopencv_features2d -lopencv_ml -lopencv_highgui -lopencv_objdetect -lopencv_contrib -lopencv_legacy';
eval(['mex CameraStreamer_.cpp MxArray.cpp ../include/video_host.h ../include/datapacket.pb.cc ../include/datapacket.pb.h -O -L../include' ' -I' OPENCV_PATH '/include ' ' -I' LOCAL_PATH LIBS])
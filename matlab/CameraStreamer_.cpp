#include "opencv2/opencv.hpp"
#include "mexopencv.hpp"
#include "../include/video_host.h"
#include <stdlib.h>

int last_id = 0;

std::map<int, VideoCaptureServer*> obj_;

void mexFunction( int nlhs, mxArray *plhs[], int nrhs, const mxArray *prhs[] )
{
	if (nrhs<1 || nlhs>1)
        mexErrMsgIdAndTxt("videohost:error","Wrong number of arguments");
    
	std::vector<MxArray> rhs(prhs,prhs+nrhs);
	int id = 0;
	std::string method;
	if (nrhs==1) {
		last_id++;
		obj_.insert(std::make_pair(last_id, new VideoCaptureServer(rhs[0].toInt())));
		plhs[0] = MxArray(last_id);
		
		return;
	}
	else if (rhs[0].isNumeric() && rhs[0].numel()==1 && nrhs>1) { //TODO co je numel?
		id = rhs[0].toInt();
		method = rhs[1].toString();
	}
	else
        mexErrMsgIdAndTxt("videohost:error","Invalid arguments");
	
	VideoCaptureServer& obj = *obj_[id];
	if (method == "grab") {
    	if (nrhs!=2)
    		mexErrMsgIdAndTxt("videohost:error","Wrong number of arguments");

		Frame frame = obj.grabFrame();

		if (frame.image.type()==CV_8UC3)
			cv::cvtColor(frame.image, frame.image, CV_BGR2RGB);
			
    	plhs[0] = MxArray(frame.image);
    } else if (method == "connect") {
    	if (nrhs!=2)
    		mexErrMsgIdAndTxt("videohost:error","Wrong number of arguments");

 		obj.waitForConnection();
    } else if (method == "disconnect") {
    	if (nrhs!=2)
    		mexErrMsgIdAndTxt("videohost:error","Wrong number of arguments");

 		obj.disconnect();
    }
    else
		mexErrMsgIdAndTxt("videohost:error","Unrecognized operation");
}

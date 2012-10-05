classdef CameraStreamer < handle
    
    properties (SetAccess = private)
        % Object ID
    	id
    end
    
    methods
        function this = CameraStreamer(portname)
   
            if nargin < 1, portname = 1212; end
            this.id = CameraStreamer_(portname);
        end
        
        function frame = grab(this)
	        frame = CameraStreamer_(this.id, 'grab');
        end

        function waitForConnection(this)
	        CameraStreamer_(this.id, 'connect');
	    end
        
        function disconnect(this)
	        CameraStreamer_(this.id, 'disconnect');
	    end
    end 
end

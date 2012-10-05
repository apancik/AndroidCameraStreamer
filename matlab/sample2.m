s = CameraStreamer(1313);
s.waitForConnection();

while(true)
    frame = s.grab(); 
    imshow(frame);
    drawnow;
end

s.disconnect();
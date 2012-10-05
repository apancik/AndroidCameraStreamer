s = CameraStreamer(1313);
s.waitForConnection();

step = 250;
counter = 0;
tic;

disp('Grabbing.');

while(true)
    counter = counter + 1;
    if mod(counter, step) == 0
        disp(step/toc);
        tic;
    end
    
    frame = rgb2gray(s.grab());
    frame = edge(frame, 'canny');
    imagesc(frame);
    drawnow;
end

s.disconnect();
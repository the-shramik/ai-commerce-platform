import { useEffect, useState } from 'react';
import '@chatscope/chat-ui-kit-styles/dist/default/styles.min.css';
import { MainContainer, ChatContainer, MessageList, Message, MessageInput, TypingIndicator } from '@chatscope/chat-ui-kit-react';

function AskAi() {
  const [messages, setMessages] = useState([]);
  const [isTyping, setIsTyping] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Initialize with the welcome message
    setMessages([
      {
        message: "Hello, I'm your personal AI!",
        sender: "AI",
        direction: "incoming"
      }
    ]);
  }, []);

  const baseUrl = import.meta.env.VITE_BASE_URL;

  const handleSend = async (message) => {
    const userMessage = {
        message: message,
        sender: "user",
        direction: "outgoing"
    };
  
    const newMessages = [...messages, userMessage];
    setMessages(newMessages);
    
    // Set typing indicator
    setIsTyping(true);
    setError(null);

    try {
      await processMessageToChatGPT(message);
    } catch (err) {
      setError(err.message);
    } finally {
      setIsTyping(false);
    }
  };

  async function processMessageToChatGPT(chatMessages) {
    try {
      const response = await fetch(`${baseUrl}/api/chat/ask?message=${encodeURIComponent(chatMessages)}`, {
        method: "GET",
        headers: {
          "Content-Type": "application/json"
        },
      });

      console.log(response, 'AskAi response');

      if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.error?.message || 'Failed to get response from TeluskoBot');
      }

      const data = await response.json();
      
      // Add the bot's response to messages
      setMessages(prevMessages => [
        ...prevMessages, 
        {
          message: data.response,
          sender: "ChatGPT",
          direction: "incoming"
        }
      ]);

    } catch (error) {
      console.error("Error processing message:", error);
      throw error;
    }
  }

  return (
    <div className="container-fluid mt-5 pt-5">
      <div className="row justify-content-center">
        <div className="col-md-10 col-lg-8">
          <div className="card shadow" style={{ height: "80vh" }}>
            <div className="card-header bg-primary text-white">
              <h5 className="mb-0">
                <i className="bi bi-robot me-2"></i>
                AI Assistant
              </h5>
            </div>
            <div className="card-body p-0" style={{ height: "calc(100% - 56px)" }}>
              <MainContainer style={{ height: "100%" }}>
                <ChatContainer style={{ height: "100%" }}>       
                  <MessageList 
                    scrollBehavior="smooth" 
                    typingIndicator={isTyping ? <TypingIndicator content="AI is typing" /> : null}
                  >
                    {messages.map((message, i) => (
                      <Message 
                        key={i} 
                        model={message}
                        className={message.error ? "error-message" : ""}
                      />
                    ))}
                  </MessageList>
                  {error && (
                    <div className="alert alert-danger m-2" role="alert">
                      <i className="bi bi-exclamation-triangle-fill me-2"></i>
                      {error}
                    </div>
                  )}
                  <MessageInput 
                    placeholder="Type your message here..." 
                    onSend={handleSend}
                    attachButton={false}
                  />        
                </ChatContainer>
              </MainContainer>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default AskAi;